package org.jresearch.gwt.tool.emu.apt;

public class State {

	private boolean changed;

	public boolean isChanged() {
		return changed;
	}

	public void setChanged(boolean changed) {
		this.changed = changed;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (changed ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		State other = (State) obj;
		if (changed != other.changed)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "State [changed=" + changed + "]";
	}

}
