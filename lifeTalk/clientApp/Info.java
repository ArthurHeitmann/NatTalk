package lifeTalk.clientApp;

public class Info {
	private static String[] args;
	public static final String appName = "Life_Talk";
	public static final String version = "alpha";

	public static void setArgs(String[] args) {
		if (args == null)
			Info.args = args;

	}

	public static String[] getArgs() {
		return args;
	}

}
