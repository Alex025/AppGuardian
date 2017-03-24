/* The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Nan Zhang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <list>
#include <map>
#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <pthread.h>
#include <pwd.h>

#include "scanner.h"

//#define DEBUG

#ifdef DEBUG
#define LOG_TAG "ScannerLib"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

std::vector<std::string> whitelist;
std::list<std::string> return_list;
bool jobStatus = false;
int working_threads;
pthread_mutex_t lock;
pthread_mutex_t return_lock;

#ifdef __cplusplus
extern "C" {
#endif

int MAX_TIME;
int INTERVAL;
int WARNING_LIMIT;
double START_TIME;

static char string_buffer[256];

/* return current time in milliseconds */
static double now_ms() {
	struct timespec res;
	clock_gettime(CLOCK_MONOTONIC, &res);
	return 1000.0 * res.tv_sec + (double) res.tv_nsec / 1e6;
}

char *nexttoksep(char **strp, char *sep) {
	char *p = strsep(strp, sep);
	return p;
}
char *nexttok(char **strp) {
	return nexttoksep(strp, " ");
}

char *nextline(char **strp) {
	return nexttoksep(strp, "\n");
}

int readAllState(std::string name, int pid, int tid, int nr_switch,
		std::vector<tinfo> *p) {
	char schedline[1024];
	int r;
	char *tname;
	int utime = 0, stime = 0;
	FILE *fd;
	char *ptr;
	double time;

	time = now_ms();
	// read tname, utime, stime
	if (tid != 0) {
		sprintf(schedline, "/proc/%d/task/%d/stat", pid, tid);
	} else {
		sprintf(schedline, "/proc/%d/stat", pid);
	}
	fd = fopen(schedline, "r");
	if (fd == 0) {
#ifdef DEBUG
		LOGE("open %s fail!", schedline);
#endif
		return -1;
	}
	r = fread(schedline, 1023, 1, fd);
	fclose(fd);
	if (r < 0) {
#ifdef DEBUG
		LOGE("read %s fail!", schedline);
#endif
		return -1;
	}

	ptr = schedline;
	nexttok(&ptr); // skip pid
	ptr++;          // skip "("

	tname = ptr;
	ptr = strrchr(ptr, ')'); // Skip to *last* occurence of ')',
	*ptr++ = '\0';           // and null-terminate name.

	ptr++;         // skip " "
	nexttok(&ptr); //state
	nexttok(&ptr); //ppid
	nexttok(&ptr); // pgrp
	nexttok(&ptr); // sid
	nexttok(&ptr); //tty;
	nexttok(&ptr); // tpgid
	nexttok(&ptr); // flags
	nexttok(&ptr); // minflt
	nexttok(&ptr); // cminflt
	nexttok(&ptr); // majflt
	nexttok(&ptr); // cmajflt
	utime = atoi(nexttok(&ptr));
	stime = atoi(nexttok(&ptr));

	std::string tname_string(tname);

	struct tinfo thread_info(name, pid, tid, time, nr_switch, utime, stime,
			tname_string);
	p->push_back(thread_info);
}

void *thread_monitorprocess(void* arg) {
	struct tinfo process_info = *(struct tinfo *) arg;
	DIR *d;
	struct dirent *de;
	int tidfilter = 0;
	char task[50];
	char schedline[1024];
	long t1, t2;
	int nr_switch;
	int r;
	char *tname;
	FILE *fd;
	double time;
	std::map<int, std::vector<tinfo> *> threadlist;
	int timer = 0;
	char buffer[6];

	while (1) {
		timer++;
		sprintf(task, "/proc/%d/task/", process_info.pid);
		d = opendir(task);
		if (d == 0) {
#ifdef DEBUG
			LOGE("process %d dir task open failed", process_info.pid);
#endif
			pthread_mutex_lock(&lock);
			working_threads--;
			pthread_mutex_unlock(&lock);
			return 0;
		}

		while ((de = readdir(d)) != 0) {
			if (isdigit(de->d_name[0])) {
				int tid = atoi(de->d_name);
				if (!tidfilter || (tidfilter == tid)) {
					sprintf(schedline, "/proc/%d/task/%d/schedstat",
							process_info.pid, tid);

					fd = fopen(schedline, "r");
					if (fd == 0) {
#ifdef DEBUG
						LOGE("open %s fail!", schedline);
#endif
						pthread_mutex_lock(&lock);
						working_threads--;
						pthread_mutex_unlock(&lock);
						return 0;
					}
					fscanf(fd, "%ld %ld %d", &t1, &t2, &nr_switch);
					fclose(fd);

					std::map<int, std::vector<tinfo> *>::iterator pos =
							threadlist.find(tid);
					if (pos != threadlist.end()) {
						std::vector<tinfo> *p = (*pos).second;
						struct tinfo old_info = p->back();
						if (old_info.nr_switch != nr_switch) {
							r = readAllState(process_info.name,
									process_info.pid, tid, nr_switch, p);
						}
					} else {
						std::vector<tinfo> *p = new std::vector<tinfo>;
						r = readAllState(process_info.name, process_info.pid,
								tid, nr_switch, p);
						threadlist.insert(threadlist.end(),
								std::pair<int, std::vector<tinfo>*>(tid, p));
					}
				}
			}
		}
		if (d) {
			closedir(d);
		}
		if (now_ms() - MAX_TIME * INTERVAL / 1000 > START_TIME || r == -1) {
			std::map<int, std::vector<tinfo>*>::iterator it =
					threadlist.begin();
			for (; it != threadlist.end(); ++it) {
				std::vector<tinfo> *p = it->second;
#ifdef DEBUG
				if (p->size() == 0) {
					LOGE("ERROR\n");
				}
#endif
				if (p->size() - 1 >= WARNING_LIMIT) {
					pthread_mutex_lock(&return_lock);
					snprintf(buffer, sizeof(buffer), "%d", (*p)[0].pid);
					return_list.push_back(buffer);
					pthread_mutex_unlock(&return_lock);
#ifdef DEBUG
					LOGE("WARNING: pid = %d, name = %s, tid = %d, size = %d\n",
							(*p)[0].pid, (*p)[0].name.c_str(), (*p)[0].tid,
							p->size());
#endif
				}
			}
//			fprintf(fd, ">>>>>>ERROR, ABORTED<<<<<<");
#ifdef DEBUG
			LOGI("Thread for pid %d finishes its job!", process_info.pid);
#endif
//			LOGE("DONE!");
			pthread_mutex_lock(&lock);
			working_threads--;
			pthread_mutex_unlock(&lock);
			return 0;
		}
		usleep(INTERVAL);
	}
}

int readWhiteList() {
	FILE *fp;
	int buff_size = 100;
	if ((fp = fopen("/data/data/edu.iub.seclab.appguardian/files/whitelist.txt", "r")) == NULL) {
#ifdef DEBUG
		LOGE("can not open file of whitelist.txt\n");
#endif
		return -1;
	}
	char *name = (char *) malloc(buff_size * sizeof(char));
	while (fgets(name, buff_size, fp) != NULL) {
		char *p = name;
		while (*p) {
			if (*p == '\n') {
				*p = '\0';
			}
			++p;
		}
		std::string temp(name);
		whitelist.push_back(temp);
	}
	fclose(fp);
	delete name;
}

int init() {
	DIR *d;
	struct dirent *de;
	int pidfilter = 0;
	char statline[1024];
	char cmdline[1024];
	int fd, r;
	struct stat stats;
	struct passwd *pw;

	std::map<int, std::vector<tinfo> *> processlist;
	int timer = 0;

	readWhiteList();

	START_TIME = now_ms();

	while (1) {
		timer++;

		d = opendir("/proc");
		if (d == 0) {
#ifdef DEBUG
			LOGE("open %s fail!", "/proc");
#endif
			return -1;
		}

		while ((de = readdir(d)) != 0) {
			if (isdigit(de->d_name[0])) {
				int pid = atoi(de->d_name);
				if (!pidfilter || (pidfilter == pid)) {
					sprintf(statline, "/proc/%d", pid);
					sprintf(cmdline, "/proc/%d/cmdline", pid);
					stat(statline, &stats);

					if (stats.st_uid >= 10000) {
						fd = open(cmdline, O_RDONLY);
						if (fd == 0) {
#ifdef DEBUG
							LOGE("open %s fail!", cmdline);
#endif
							continue;
						} else {
							r = read(fd, cmdline, 1023);
							close(fd);
							if (r < 0) {
#ifdef DEBUG
								LOGE("read %s fail!", cmdline);
#endif
								continue;
							}
						}
						cmdline[r] = 0;
						std::string item(cmdline);
						if (std::find(whitelist.begin(), whitelist.end(), item)
								== whitelist.end()) {
							//not in the white list
							std::map<int, std::vector<tinfo> *>::iterator pos =
									processlist.find(pid);
							if (pos == processlist.end()) {
								std::vector<tinfo> *p = new std::vector<tinfo>;
								if (r != -1) {
									struct tinfo *process_info = new tinfo(item,
											pid);
									processlist.insert(processlist.end(),
											std::pair<int, std::vector<tinfo>*>(
													pid, p));
#ifdef DEBUG
									LOGI(
											"pid = %d, name = %s, tid = %d, nr_switch = %d\n",
											process_info->pid,
											process_info->name.c_str(),
											process_info->tid,
											process_info->nr_switch);
#endif
									pthread_t pt;
									pthread_create(&pt, NULL,
											&thread_monitorprocess,
											(void *) process_info);
									pthread_mutex_lock(&lock);
									working_threads++;
									pthread_mutex_unlock(&lock);
								}
							}
						}
					}
				}
			}
		}
		closedir(d);
		if (now_ms() - MAX_TIME * INTERVAL / 1000 > START_TIME) {
#ifdef DEBUG
			LOGI("Main Process finishes its job!");
#endif
			jobStatus = true;
			return 0;
		}
		usleep(INTERVAL);
	}
	return 0;
}

JNIEXPORT jobjectArray JNICALL Java_edu_iub_seclab_appguardian_AppGuardianService_beginScanning(
		JNIEnv *env, jobject obj, jint max_time, jint interval,
		jint warn_limit) {
	jobjectArray ret;

#ifdef DEBUG
	LOGI("beginScanning!");
#endif
	return_list.clear();

	working_threads = 0;
	if (pthread_mutex_init(&lock, NULL) != 0) {
		return NULL;
	}
	if (pthread_mutex_init(&return_lock, NULL) != 0) {
		return NULL;
	}
	MAX_TIME = max_time;
	INTERVAL = interval * 1000;
	WARNING_LIMIT = warn_limit;
	jobStatus = false;
	init();
	while (1) {
		pthread_mutex_lock(&lock);
		if (working_threads == 0) {
			pthread_mutex_unlock(&lock);
			ret = (jobjectArray) env->NewObjectArray(return_list.size(),
					env->FindClass("java/lang/String"), env->NewStringUTF(""));
			int i = 0;
			std::list<std::string>::const_iterator iter;
			for (iter = return_list.begin(); iter != return_list.end();
					++iter) {
				env->SetObjectArrayElement(ret, i, env->NewStringUTF((*iter).c_str()));
				i++;
			}
			jobStatus = false;
			return ret;
		}
		pthread_mutex_unlock(&lock);
		usleep(10);
	}
	jobStatus = false;
	return 0;
}

JNIEXPORT jboolean JNICALL Java_edu_iub_seclab_appguardian_AppGuardianService_stopTask(
		JNIEnv *env, jobject obj) {
	START_TIME = 0;
#ifdef DEBUG
	LOGE("STOPPING TASKS...");
#endif
	while (working_threads > 0 || jobStatus == true) {
		usleep(1000);
	}
#ifdef DEBUG
	LOGE("STOPPING TASKS FINISHED");
#endif
	return true;
}

JNIEXPORT jboolean JNICALL Java_edu_iub_seclab_appguardian_AppGuardianMainActivity_checkScanStatus(
		JNIEnv *env, jobject obj, jboolean status) {
	status = jobStatus;
	return status;
}

#ifdef __cplusplus
}
#endif
