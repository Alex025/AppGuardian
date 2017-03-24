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

#include <string>

struct time_info {
	int interval;
	int times;
};

struct tinfo {
    std::string name;
    int pid;
    int tid;
    double timestamp;
    int nr_switch;
    int utime;
    int stime;
    std::string tname;
    tinfo(std::string a, int b, int c, int d, int e, int f, int g, std::string k) {
    	name = a;
    	pid = b;
    	tid = c;
    	timestamp = d;
    	nr_switch = e;
    	utime = f;
    	stime = g;
    	tname = k;
    }
    tinfo(std::string a, int b) {
    	name = a;
    	pid = b;
    	tid = 0;
    	timestamp = 0;
    	nr_switch = 0;
    	utime = 0;
    	stime = 0;
    	tname = "";
    }
};

