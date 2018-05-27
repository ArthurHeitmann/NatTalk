package lifeTalk.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lifeTalk.jsonRW.FileRW;
import lifeTalk.jsonRW.server.ServerStartupOperations;

/**
 * This class with it's subclass establishes connections with clients and than either logs
 * them in or registers them to the server.
 * 
 * @author Arthur H.
 *
 */
public class Server {
	/**
	 * An array of all users with their user names passwords etc.
	 */
	private static JsonArray loginsData;
	/**
	 * File location of the JSON file with the login data of the users
	 */
	private static final String JSONLOCATION = Server.class.getResource("data/logins.json").toExternalForm();
	/**
	 * Java representation of the JSON file with the user data
	 */
	private static JsonObject loginJson;

	public static void main(String[] args) throws IOException {
		Info.setArgs(args);
		//Create a server at port 2111
		ServerSocket server = new ServerSocket(2111);
		loginJson = new JsonParser().parse(FileRW.readFromFile(JSONLOCATION)).getAsJsonObject();
		loginsData = loginJson.get("users").getAsJsonArray();
		boolean connected = false;
		System.out.println("Server running");
		while (true) {
			try {
				//Wait for a connection with a client and than start a 
				//new thread to manage the client and than keep waiting for new connections
				new CLientHandler(server.accept()).start();
				connected = true;
			} catch (IOException e) {
				if (Boolean.parseBoolean(Info.getArgs()[0]))
					e.printStackTrace();
			} finally {
				if (!connected) {
					server.close();
					System.out.println("Closing server");
					connected = false;
				}
			}
		}

	}

	/**
	 * This class handles one client and his requests
	 * 
	 * @author Arthur H.
	 *
	 */
	private static class CLientHandler extends Thread {
		//whether the user successfully logged in
		//Interface to communicate with the client
		private Socket socket;
		//read the incoming data from the client
		private ObjectInputStream in;
		//send data to the client
		private ObjectOutputStream out;

		/**
		 * Setup the handle with the socket and input/output devices.
		 * 
		 * @param socket Socket with the client
		 * @throws IOException When an IOXceptiion happens
		 */
		public CLientHandler(Socket socket) throws IOException {
			this.socket = socket;
			in = new ObjectInputStream(socket.getInputStream());
			out = new ObjectOutputStream(socket.getOutputStream());
		}

		/**
		 * This method get's called once the initialization is completed and than waits
		 * for the user to request a login or register a new account
		 */
		@Override
		public void run() {
			//becomes true once the user has successfully logged in or registered
			boolean userAllowed = false;
			String name = null;
			try {
				System.out.println("Client running");
				//the user is only allowed to enter wrong login credentials 4 time
				int triesLeft = 4;
				ServerStartupOperations.setFileLocation(JSONLOCATION);
				while (true) {
					//receive action command, user name, password and whether to stay logged in or not from the client
					String action = (String) in.readObject();
					boolean stayLoggedin = Boolean.parseBoolean((String) in.readObject());
					if (action == null) {
						break;
					}
					String usrName = (String) in.readObject();
					String pw = action.equals("AUTOLOGIN") ? (String) in.readObject() : hashGenerator((String) in.readObject());
					if (usrName == null || pw == null) {
						break;
					}
					//IF the user wants to simply login with an existing user account
					if (action.equals("LOGIN")) {
						//check whether the username and pw are correct
						if (ServerStartupOperations.logUserIn(usrName, pw, JSONLOCATION)) {
							//if the user wants to stay logged in send a loginID
							if (stayLoggedin) {
								write("SUCCESS LOGIN");
								name = usrName;
								//Generated by getting the current time in nano seconds and hash that through SHA-256
								String id = hashGenerator(Long.toString(System.nanoTime()));
								write(id);
								ServerStartupOperations.createLoginID(usrName, id);
							} else {
								name = usrName;
								write("SUCCESS");
							}
							userAllowed = true;
							break;
						}
						//If the login credentials are invalid lower the tries the user has left before being blocked 
						else if (triesLeft > 0) {
							triesLeft--;
							write("INVALID INPUTS");
						}
						//once the user has no more tries left the server closes the connection
						else if (triesLeft < 1) {
							write("ACCESS DENIED");
							socket.close();
							break;
						}
					}
					//When the client application tries to automatically login at startup 
					//if the id is invalid nothing happens and the proceeds as if nothing happened
					else if (action.equals("AUTOLOGIN")) {
						if (ServerStartupOperations.validLoginID(usrName, pw)) {
							userAllowed = true;
							name = usrName;
							write("SUCCESS");
							break;
						} else {
							write("DENIED");
						}
					}
					//the user prompts to register a new account
					else if (action.equals("REGISTER")) {
						//try to create a new account
						if (ServerStartupOperations.registerUser(usrName, pw, JSONLOCATION)) {
							write("SUCCESS");
							userAllowed = true;
							name = usrName;
							break;
						} else {
							write("USERNAME TAKEN");
						}
					}
				}
			}
			//when something goes wrong while communicating with the client
			catch (IOException | ClassNotFoundException | URISyntaxException e) {
				if (Boolean.parseBoolean(Info.getArgs()[0]))
					e.printStackTrace();
				try {
					write("ERROR");
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			//close the connection once all actions have ended
			finally {
				try {
					if (!userAllowed) {
						in.close();
						out.close();
						socket.close();
					}

				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.out.println("Connection closed");
			}
			if (userAllowed) {
				Thread t = new Thread(new ServerSideToClient(socket, name, in, out));
				t.start();
			}
		}

		/**
		 * Send a message to the client
		 * 
		 * @param msg Message
		 * @throws IOException
		 */
		private void write(Object obj) throws IOException {
			out.writeObject(obj);
			out.flush();
		}

		/**
		 * Hash a given string using SHA-256
		 * 
		 * @param stringToHash The input String that is to be hashed
		 * @return The hashed string
		 */
		private String hashGenerator(String stringToHash) {
			//adds some salt to the text
			stringToHash += "(`5#c&(\\zPU]'s`Y`6e@x\"h%MwE8=_z{";
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				//convert string to byte array and hash them
				byte[] bytes = md.digest(stringToHash.getBytes());
				StringBuilder sb = new StringBuilder();
				//convert the byte array to a string
				for (byte b : bytes) {
					sb.append(Integer.toString((b & 0xff) + 0x100, 16));
				}
				return sb.toString();

			} catch (NoSuchAlgorithmException e) {
				//shouldn't occur
				e.printStackTrace();
				return stringToHash;
			}
		}
	}

}
