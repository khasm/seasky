package mie;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import mie.utils.Command;
import mie.utils.TestSetGenerator;
import mie.utils.ScriptErrorException;

public class Main {

	private static String scriptPath = "/scripts";
	
	public static void main(String[] args) throws IOException {
		if(args.length == 0){
			printHelp();
		}
		else{
			int nextArg = 0;
			List<String> terminalArgs = new LinkedList<String>();
			List<Command> queue = new LinkedList<Command>();
			while(nextArg < args.length){
				if(args[nextArg].equalsIgnoreCase("script")){
					try{
						queue.add(new Command(args[nextArg], new String[]{args[nextArg+1]}));
						nextArg += 2;
					}
					catch(ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("-sp")){
					try{
						scriptPath = args[nextArg+1];
						nextArg += 2;
					}
					catch(ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else{
					terminalArgs.add(args[nextArg]);
					nextArg++;
				}
			}
			for(Command command: queue){
				if(command.getOp().equalsIgnoreCase("script")){
					File script = new File(new File(scriptPath), command.getArgs()[0]);
					if(script.exists()){
						TestSetGenerator test = new TestSetGenerator(script);
						try{
							test.generateTestSets();
						}
						catch(ScriptErrorException e){
							e.printStackTrace();
						}
					}
				}
			}
			if(!terminalArgs.isEmpty()){
				TestSetGenerator test = new TestSetGenerator(terminalArgs.toArray(new String[0]));
				try{
					test.generateTestSets();
				}
				catch(ScriptErrorException e){
					e.printStackTrace();
				}
			}
		}
	}

	//TODO: update
	private static void printHelp(){
		System.out.println("Must supply an operation:");
		System.out.println("add[[d]mime] <first> [last]");
		System.out.println("index");
		System.out.println("search[[d]mime] <first> [last]");
		System.out.println("get[mime] <first> [last]");
		System.out.println("print");
		System.exit(1);
	}
}
