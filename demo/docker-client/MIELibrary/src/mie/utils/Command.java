package mie.utils;

public class Command {

	private String op;
	private String[] args;

	public Command(String op){
		this.op = op;
		this.args = new String[0];
	}

	public Command(String op, String[] args){
		this.op = op;
		this.args = args;
	}

	public String getOp() {
		return op;
	}

	public String[] getArgs() {
		return args;
	}
}