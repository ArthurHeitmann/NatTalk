package lifeTalk.clientApp;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import lifeTalk.jsonRW.client.ClientStartupOperations;

/**
 * Class to handle events from the GUI application, like connecting to the server, logging
 * in or registering. Also takes into consideration if a users want's or is supposed to
 * automatically log in
 * 
 * @author Arthur H.
 *
 */
public class LoginController extends Thread {
	@FXML
	private TextField srvAdrInput;
	@FXML
	private TextField portInput;
	@FXML
	private TextField uNameInp;
	@FXML
	private TextField pwInp;
	@FXML
	private TextField uNameInpReg;
	@FXML
	private TextField pwInpReg;
	@FXML
	private Text infoText;
	@FXML
	private GridPane connectPane;
	@FXML
	private TabPane loginTabs;
	@FXML
	private GridPane loginGrid;
	@FXML
	private GridPane registerGrid;
	@FXML
	private TextField pwInpConf;
	@FXML
	private CheckBox connectedCheckBox;
	@FXML
	private CheckBox loggedinCheckBox;
	@FXML
	private ImageView loadingGIF1;
	private boolean autoLogin = false;
	private Stage mainStage;
	private ClientStartConnection connection = new ClientStartConnection();

	/**
	 * This method is being called when auto Connect is enabled. See Main class
	 * 
	 * @param address Server address
	 * @param port Server port
	 */
	public void autoConnect(String address, int port) {
		srvAdrInput.setText(address);
		portInput.setText(Integer.toString(port));
		connectToServer(null);

	}

	/**
	 * Try to connect to a server using the server address and port from the GUI
	 * application. If successful switch to login/register form
	 * 
	 * @param event
	 */
	public void connectToServer(ActionEvent event) {
		try {
			//User info
			loadingGIF1.setVisible(true);
			setInfoText("Connecting");
			//save address and port into variables to save some typing work
			String srvAdr = srvAdrInput.getText();
			int port = Integer.parseInt(portInput.getText());
			if (srvAdr == null || srvAdr.isEmpty() || port < 1) {
				setInfoText("Inputs must be not empty!");
				return;
			}
			//tries to connect to server
			if (connection.connectToServer(srvAdr, port)) {
				//save auto login informations to startup file
				if (connectedCheckBox.isSelected()) {
					ClientStartupOperations.setServerInfo(srvAdr, port);
				}
				setInfoText("Connected");
				//animation for switching between server connect form and login/register form
				Timeline hideAnimation = new Timeline();
				hideAnimation.getKeyFrames().addAll(new KeyFrame(Duration.ZERO, new KeyValue(connectPane.translateXProperty(), 0)),
						new KeyFrame(Duration.millis(100), new KeyValue(connectPane.translateXProperty(), 15)),
						new KeyFrame(Duration.millis(450), new KeyValue(connectPane.translateXProperty(), -300)));
				hideAnimation.setOnFinished(e -> connectPane.setVisible(false));

				Timeline showAnimation = new Timeline();
				showAnimation.getKeyFrames().addAll((new KeyFrame(Duration.ZERO, new KeyValue(loginTabs.translateXProperty(), -335))),
						new KeyFrame(Duration.millis(400), new KeyValue(loginTabs.translateXProperty(), 15)), new KeyFrame(Duration.millis(475), new KeyValue(loginTabs.translateXProperty(), 0)));
				showAnimation.setDelay(Duration.millis(75));

				hideAnimation.play();
				showAnimation.play();
				setInfoText("");
				loadingGIF1.setVisible(false);
				//check whether remember login was used before. If yes automatically log the user in
				connection.autoLogin(this, mainStage);
			} else {
				setInfoText("Couldn't connect to server");
			}
		} catch (IllegalArgumentException e) {
			setInfoText("Invalid port number");
			if (Boolean.parseBoolean(Info.getArgs()[0]))
				e.printStackTrace();
		} catch (Exception e) {
			setInfoText("An Error occured");
			if (Boolean.parseBoolean(Info.getArgs()[0]))
				e.printStackTrace();
		} finally {
			loadingGIF1.setVisible(false);
		}
	}

	/**
	 * Method get's called when the user tries to login. Tries to login to the server
	 * using the login credentials the user entered. Allows to stay logged in
	 * 
	 * @param event
	 */
	public void login(ActionEvent event) {
		try {
			loadingGIF1.setVisible(true);
			//get inputs
			String uName = uNameInp.getText();
			String pw = pwInp.getText();
			if (uName == null || uName.isEmpty() || pw == null || pw.isEmpty()) {
				setInfoText("Inputs must be not empty!");
				return;
			}
			//try to login to the server. If successful switch to the users chat window
			if (connection.loginToServer(uNameInp.getText(), pwInp.getText(), autoLogin ? "AUTOLOGIN" : "LOGIN", loggedinCheckBox.isSelected())) {
				//if the user checked the stay logged in check box save that into the startup file
				if (loggedinCheckBox.isSelected()) {
					ClientStartupOperations.setAutoLogin(true, uName, ClientStartupOperations.getloginID());
				}
				try {
					if (event.getSource().getClass().getName().equals("javafx.scene.control.Button"))
						switchScene(((Node) event.getSource()).getScene());
					else
						switchScene((Scene) event.getSource());
				} catch (IOException e) {
					infoText.setText("Couldn't switch to chats window");
					if (Boolean.parseBoolean(Info.getArgs()[0]))
						e.printStackTrace();
				}
			} else {
				if (autoLogin)
					autoLogin = false;
				pwInp.clear();
				setInfoText("Couldn't log in, please try again");
			}
		}
		//Get's thrown when the user entered wrong credentials 4 times
		//closes the application
		catch (LoginException e) {
			setInfoText("Server closed the connection");
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			System.exit(0);
		} catch (Exception e) {
			if (Boolean.parseBoolean(Info.getArgs()[0]))
				e.printStackTrace();
			setInfoText("An error occured");
		} finally {
			loadingGIF1.setVisible(false);
		}

	}

	/**
	 * Method get's called when the user tries to register a new account. Tries to
	 * register to the server using the register credentials the user entered
	 * 
	 * @param event
	 */
	public void register(ActionEvent event) {
		//get inputs
		String uName = uNameInpReg.getText();
		String pw = pwInpReg.getText();
		String pwConf = pwInpConf.getText();
		if (uName == null || uName.isEmpty() || pw == null || pw.isEmpty()) {
			setInfoText("Inputs must be not empty!");
			return;
		}

		//verify validity of the username and password
		Pattern uNamePattern = Pattern.compile("[^a-zA-Z0-9._!§$%&\\/()=?+#-*:;{}<>°´`²³öäüß\\[\\]]");
		boolean uNameAllowed = uNamePattern.matcher(uName).find();
		Pattern pwPattern = Pattern.compile("[^1-9a-z-A-Z_.:#+-öäüß*/!$%&/()=?°<>|\\[\\]]");
		boolean pwAllowed = pwPattern.matcher(pw).find();

		if (uNameAllowed) {
			infoText.setText("Username contains illeagal characters");
			return;
		}
		if (pwAllowed) {
			infoText.setText("Password contains illeagal characters");
			return;
		}
		if (uName.length() > 26 || uName.length() < 1) {
			infoText.setText("Username must contain 1 - 26 characters");
			return;
		}
		if (pw.length() > 41 || pw.length() < 1) {
			infoText.setText("Password must contain 1 - 40 characters");
		}

		//check whether the confirmation password equals the first password
		else if (!pw.equals(pwConf)) {
			setInfoText("Passwords must be the same");
			return;
		}
		try {
			//try to register to the server and if successful switch to chats window
			if (connection.loginToServer(uNameInpReg.getText(), pwInpReg.getText(), "REGISTER", false)) {
				setInfoText("");
				try {
					switchScene(((Node) event.getSource()).getScene());
				} catch (IOException e) {
					infoText.setText("Couldn't switch to chats window");
					if (Boolean.parseBoolean(Info.getArgs()[0]))
						e.printStackTrace();
				}
			} else {
				setInfoText("Couldn't register, please try again\n (Maybe the username is not available)");
			}
		} catch (LoginException e) {
			//Exception can not happen in this method only when logging in
		}
	}

	/**
	 * Display information text
	 * 
	 * @param text
	 */
	public void setInfoText(String text) {
		infoText.setText(text);
	}

	/**
	 * Switches from the current view to the chats screen
	 * 
	 * @param scene The currently active scene
	 * @throws IOException
	 */
	private void switchScene(Scene scene) throws IOException {
		FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ChatsScreen.fxml"));
		scene.setRoot(fxmlLoader.load());
		new ClientSideToServer(connection.getSocket(), fxmlLoader.getController(), connection.getOut(), connection.getIn()).retrieveUserInfo();
		System.out.println(uNameInp.getText() + "logged in");

	}

	/**
	 * Fill in the login field when auto login is enabled
	 * 
	 * @param uName Username
	 * @param pw Password
	 * @param autLoginEnabled
	 */
	public void setupAutologin(String uName, String pw, boolean autLoginEnabled) {
		uNameInp.setText(uName);
		pwInp.setText(pw);
		autoLogin = autLoginEnabled;
	}

	/**
	 * Set the current stage of the window
	 * 
	 * @param stage
	 */
	public void setMainStage(Stage stage) {
		mainStage = stage;
	}
}
