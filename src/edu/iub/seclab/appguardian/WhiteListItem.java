package edu.iub.seclab.appguardian;


import android.graphics.drawable.Drawable;

public class WhiteListItem {

	String name = null;
	String packageName = null;
	Drawable icon = null;
	boolean selected = false;

	public WhiteListItem(String name, String packageName, Drawable icon, boolean selected) {
		super();
		this.name = name;
		this.packageName = packageName;
		this.icon = icon;
		this.selected = selected;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public boolean isSelected() {
		return selected;
	}
	
	public Drawable getIcon() {
		return icon;
	}

	public void setIcon(Drawable icon) {
		this.icon = icon;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}
}
