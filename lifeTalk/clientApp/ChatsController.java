package lifeTalk.clientApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import com.google.gson.Gson;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;
import lifeTalk.clientApp.fxPresets.ChatDateInoFx;
import lifeTalk.clientApp.fxPresets.ChatcontactFx;
import lifeTalk.clientApp.fxPresets.MessageFx;
import lifeTalk.jsonRW.Message;

/**
 * The controller class of the chat screen. Holds nodes from the fxml file, so that they
 * can be accessed and changed.
 * 
 * 
 * @author Arthur H.
 *
 */
public class ChatsController {
	/**
	 * Holds a list of the contacts and chats the user has (element ->
	 * fxPresets.ChatcontactFx)
	 */
	@FXML
	private VBox chatList;
	/** Displays the username of the current user */
	@FXML
	private Label nameTitle;
	/**
	 * Profile pic of the user the current user just selected from the list (VBox
	 * chatList)
	 */
	@FXML
	private ImageView chatPImg;
	/** Name of the user the current user just selected from the list (VBox chatList) */
	@FXML
	private Label chatPName;
	/**
	 * Status info of the user the current user just selected from the list (VBox
	 * chatList)
	 */
	@FXML
	private Label chatPInfo;
	/** Holds all message from the current chat (messages -> fxPresets.MessageFx) */
	@FXML
	private VBox chatView;
	/** ScrollPane of the current chat which holds chatView */
	@FXML
	private ScrollPane chatViewScrollPane;
	/** Profile picture of the current user */
	@FXML
	private ImageView userProfilePic;
	/** Text field where the user can enter text and send it to the chat */
	@FXML
	private TextField msgInp;
	/** An info dialogue that appears i. e. when an error occurs to notify the user */
	@FXML
	private VBox infoDialogue;
	@FXML
	private ImageView onlineStatusImg;
	@FXML
	private VBox contactRequest;
	@FXML
	private HBox mainLayout;
	@FXML
	private Button contactRequestBtn;
	@FXML
	private Button contactRDialogueCloseBtn;
	@FXML
	private HBox chatState;
	@FXML
	private ImageView chatStateArrow;
	@FXML
	private TextField contactRTField1, contactRTField2;
	@FXML
	private Text contactRResult;
	@FXML
	private Button acceptBtn, declineBtn, blockBtn;
	@FXML
	private StackPane fullScreenLoading;
	@FXML
	private TextField chatSearch;
	/** The class that communicates with the server */
	private ClientSideToServer serverCommunication;
	/** Name of the person the user chats with */
	private String selectedChatContact;
	/** Object of the currently selected chat */
	private ChatcontactFx selectedContact;
	/** TRUE: if the user just clicked on a chat and an animation is still playing */
	private boolean switchingBlocked = false;
	/** List of all contacts/chats */
	private ArrayList<ChatcontactFx> contacts = new ArrayList<>();
	/** Tells which index a chat has in the chats list associated with a username */
	private HashMap<String, Integer> contactsIndex = new HashMap<>();
	/**
	 * used to compare to new message and determine whether to place a date separator or
	 * not
	 */
	private MessageFx previousMsg;
	/** null if no message is currently being received otherwise a life message */
	private MessageFx writingMsg;
	private ChangeListener<String> textInpListener;
	/**
	 * Various animations (show/hide contact request accepting/declining/blocking;
	 * show/hide the new contact request window; self explaining)
	 */
	private Timeline chatStateShow, chatStateHide, windowDisplay, windowhiding, showMsgs;
	/** whether the menu to accept/deny/block contacts is visible or not */
	private boolean chatStateVisible = false;
	/** the current window */
	private Stage window;
	/** image indicators whether a person is online or not */
	private Image statusUnknown, online, offline;
	/** Json formatting tool */
	private Gson gson = new Gson();
	/** unnecessary variable but java */
	private MessageFx[] bsMsgs;

	/**
	 * Call every 0.5 seconds the server communication class and let it check whether
	 * there are any update (like new messages or updated profile infos).
	 */
	public void basicSetup() {
		//updating
		window = (Stage) chatView.getScene().getWindow();
		Task<Void> updater = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				while (true) {
					serverCommunication.update();
					Thread.sleep(Integer.parseInt(Info.getArgs()[1]));
				}
			}
		};
		Thread thread = new Thread(updater);
		thread.start();
		window.setOnCloseRequest(e -> {
			System.out.println("exiting application");
			updater.cancel();
			removeLiveMessage();
		});

		//life message: send the currently written message to the server whenever the textProperty changes
		textInpListener = (obsV, oldV, newV) -> {
			try {
				if (!newV.isEmpty() && !oldV.equals(newV)) {
					serverCommunication.setBlocking(true);
					serverCommunication.write("msgPart");
					serverCommunication.write(new Message(msgInp.getText(), System.currentTimeMillis(), nameTitle.getText(), selectedChatContact, false));
					serverCommunication.setBlocking(false);
					//indicate that this message has been sent or removed
				} else if (newV.length() == 0 && oldV.length() > 0) {
					serverCommunication.setBlocking(true);
					serverCommunication.write("msgPart");
					serverCommunication.write(selectedChatContact);
					serverCommunication.setBlocking(false);
				}
			} catch (IOException e) {
				if (Boolean.parseBoolean(Info.getArgs()[0]))
					e.printStackTrace();
				serverCommunication.setBlocking(false);
			}
		};
		msgInp.textProperty().addListener(textInpListener);

		//setup various animations
		Scale chatStateScale = new Scale(1, 0, 0, 0);
		chatState.getTransforms().add(chatStateScale);
		chatStateHide = new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(chatStateScale.yProperty(), 0)));
		chatStateShow = new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(chatStateScale.yProperty(), 1)));
		showMsgs = new Timeline(new KeyFrame(Duration.millis(70), //
				new KeyValue(chatViewScrollPane.translateYProperty(), 0),//
				new KeyValue(chatViewScrollPane.opacityProperty(), 1)));
		Timeline hideFirstLoading = new Timeline(new KeyFrame(Duration.millis(400), new KeyValue(fullScreenLoading.opacityProperty(), 0)));
		hideFirstLoading.setDelay(Duration.millis(100));
		hideFirstLoading.setOnFinished(e -> fullScreenLoading.setVisible(false));

		//online status of contact | image initialization
		online = new Image(this.getClass().getResource("resources/online.png").toExternalForm());
		offline = new Image(this.getClass().getResource("resources/offline.png").toExternalForm());
		statusUnknown = onlineStatusImg.getImage();

		//filter chats whenever typing something into the search bar
		chatSearch.textProperty().addListener((obsV, oldV, newV) -> {
			for (int i = 0; i < chatList.getChildren().size(); i++) {
				if (((Label) ((VBox) ((HBox) chatList.getChildren().get(i)).getChildren()//
						.get(1)).getChildren()//
								.get(0)).getText().contains(newV)) {
					((HBox) chatList.getChildren().get(i)).setVisible(true);
					((HBox) chatList.getChildren().get(i)).setManaged(true);
				} else {
					((HBox) chatList.getChildren().get(i)).setVisible(false);
					((HBox) chatList.getChildren().get(i)).setManaged(false);
				}
			}
		});

		//bind text (wrapping-)width to the width of the info dialogue
		((Text) infoDialogue.getChildren().get(0)).wrappingWidthProperty().bind(infoDialogue.widthProperty().subtract(50));

		//remove loading banner after this initialization
		hideFirstLoading.play();
	}

	/**
	 * Send a contact (/friend) request to a person
	 * 
	 * @param event
	 */
	public void makeNewContactRequest(MouseEvent event) {
		if (event.getButton() != MouseButton.PRIMARY)
			return;
		//initialize animations
		if (windowDisplay == null || windowhiding == null) {
			windowDisplay = new Timeline(new KeyFrame(Duration.millis(300), //
					new KeyValue(mainLayout.opacityProperty(), 0.5), //
					new KeyValue(contactRequest.opacityProperty(), 1),//
					new KeyValue(((StackPane) event.getSource()).opacityProperty(), 0.5)));
			windowhiding = new Timeline(new KeyFrame(Duration.millis(300), //
					new KeyValue(mainLayout.opacityProperty(), 1), //
					new KeyValue(contactRequest.opacityProperty(), 0),//
					new KeyValue(((StackPane) event.getSource()).opacityProperty(), 1)));
		}
		//show the window
		contactRequest.setVisible(true);
		contactRequest.getParent().setPickOnBounds(true);
		windowDisplay.play();
		contactRDialogueCloseBtn.setOnAction(e -> {
			windowhiding.play();
			windowhiding.setOnFinished(e1 -> {
				contactRequest.setVisible(false);
				contactRequest.getParent().setPickOnBounds(false);
			});
		});
		//setup close button
		contactRequestBtn.setOnAction(e -> {
			if (serverCommunication.sendRequest(contactRTField1.getText(), contactRTField2.getText())) {
				contactRResult.setText("Contact request sent!");
				addChatContact(contactRTField1.getText(), contactRTField2.getText(), true, "", null, new Date());
			} else
				contactRResult.setText("Coulnd't find contact");
		});
	}

	/**
	 * Show/hide the contact accept/decline/block bar
	 * 
	 * @param event
	 */
	public void displayChatStates(MouseEvent event) {
		if (event.getButton() != MouseButton.PRIMARY || selectedChatContact == null)
			return;
		if (chatStateVisible)
			chatStateHide.play();
		else
			chatStateShow.play();
		chatStateVisible = !chatStateVisible;

	}

	/**
	 * Change the chat possibilities depending on it's state. -2 -> blocked, -1 ->
	 * declined; 0 -> undecided; 1 -> accapted
	 * 
	 * @param stateCombo
	 */
	public void changeChatState(String stateCombo) {
		int state = Integer.parseInt(stateCombo.substring(0, 2).trim());
		String canBeEditedBy = stateCombo.substring(2).trim();

		if (state == 1 || state == 0)
			msgInp.setEditable(true);
		else
			msgInp.setEditable(false);

		if (state == 1) {
			acceptBtn.setDisable(true);
			declineBtn.setDisable(true);
			blockBtn.setDisable(false);
			chatPInfo.setVisible(true);
		} else {
			onlineStatusImg.setVisible(false);
			chatPInfo.setVisible(false);
			if (canBeEditedBy.equals(nameTitle.getText())) {
				switch (state) {
					case 0:
						acceptBtn.setDisable(false);
						declineBtn.setDisable(false);
						blockBtn.setDisable(true);
						break;
					case -1:
						acceptBtn.setDisable(false);
						declineBtn.setDisable(true);
						blockBtn.setDisable(false);
						break;
					case -2:
						acceptBtn.setDisable(false);
						declineBtn.setDisable(true);
						blockBtn.setDisable(true);
				}
			} else {
				acceptBtn.setDisable(true);
				declineBtn.setDisable(true);
				blockBtn.setDisable(true);
			}
		}

	}

	/**
	 * Change the chat state using one of the 3 buttons and send it to the server
	 * 
	 * @param event
	 */
	public void chatStateUserInput(ActionEvent event) {
		int state;
		switch (((Button) event.getSource()).getText()) {
			case "Accept":
				state = 1;
				break;
			case "Decline":
				state = -1;
				break;
			case "Block":
				state = -2;
				break;
			default:
				state = 0;
		}
		serverCommunication.setBlocking(true);
		try {
			serverCommunication.write("setChatState");
			serverCommunication.write(state);
		} catch (IOException e) {
			showInfoDialogue(e.getMessage());
			if (Boolean.parseBoolean(Info.getArgs()[0]))
				e.printStackTrace();
		}
		serverCommunication.setBlocking(false);
		changeChatState(state + " " + nameTitle.getText());
	}

	/**
	 * Adds a contact/chat link, to the left side list, with which the user is associated
	 * 
	 * @param title Name of the other user
	 * @param firstLine The last message from that chat
	 * @param firstLineMe Whether the last message sent is from the current user
	 * @param statusInfo The status info of the other user
	 * @param img The profile pic of the other user
	 * @param dateTime
	 */
	public void addChatContact(String title, String firstLine, boolean firstLineMe, String statusInfo, Image img, Date dateTime) {
		ChatcontactFx contactFx = new ChatcontactFx(title, firstLine, firstLineMe, statusInfo, //
				img == null ? new Image(this.getClass().getResource("resources/user.png").toExternalForm()) : img, //
				dateTime);
		HBox chatElement = contactFx.getLayout();
		//place the chat in the list according to the date when the last message has been sent (newest at top)
		if (contacts.size() == 0) {
			chatList.getChildren().add(chatElement);
			contacts.add(contactFx);
		} else {
			boolean added = false;
			//create temporary list to avoid a ConcurrentModificationException
			ArrayList<ChatcontactFx> tmpList = new ArrayList<>(contacts);
			for (ChatcontactFx contact : tmpList) {
				if (!added && contactFx.getDate().after(contact.getDate())) {
					chatList.getChildren().add(contacts.indexOf(contact), chatElement);
					contacts.add(chatList.getChildren().indexOf(chatElement), contactFx);
					added = true;
				}
			}
			if (!added) {
				chatList.getChildren().add(chatElement);
				contacts.add(0, contactFx);
			}
		}
		for (ChatcontactFx chatcontactFx : contacts) {
			contactsIndex.put(chatcontactFx.getTitle(), contacts.indexOf(chatcontactFx));
		}
		//load the chat with the other user when clicked
		chatElement.setOnMouseClicked(e -> {
			//check whether the element is already selected or not OR 
			//currently another clicking animation (duration = 250 ms) is currently in progress
			if (selectedChatContact == null) {
				onlineStatusImg.setVisible(true);
				chatStateArrow.setVisible(true);
				chatStateArrow.setDisable(false);
			}
			if (switchingBlocked || (selectedChatContact != null && selectedChatContact.equals(title)) || e.getButton() != MouseButton.PRIMARY)
				return;
			switchingBlocked = true;
			msgInp.setText("");
			//highlight the selection
			if (selectedContact != null)
				selectedContact.setSelected(false);
			contactFx.setSelected(true);
			selectedContact = contactFx;
			selectedChatContact = title;
			//setup visuals and play switching animations
			chatPName.setText(title);
			chatPInfo.setText(statusInfo);
			chatPImg.setImage(img == null ? new Image(this.getClass().getResource("resources/user.png").toExternalForm()) : img);
			onlineStatusImg.setImage(statusUnknown);
			swipeChat(title);
			try {
				serverCommunication.updateChatState();
			} catch (ClassNotFoundException | IOException e1) {
				e1.printStackTrace();
			}
			contactFx.clearNewMsgCounter();
		});
	}

	/**
	 * Animates the chat from the list and the previous chat. <br>
	 * chat from the list: enlarge and fade <br>
	 * current chat: swipe up, load new messages, show new messages
	 * 
	 * @param uName
	 */
	private void swipeChat(String uName) {
		try {
			bsMsgs = serverCommunication.getMessages(uName);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Scale scaleCurrent = new Scale(1, 1, 0, 50);
		//animate chat from list (x and y scale and opacity) and current chat (translateY and opacity)
		Timeline scaleAnim = new Timeline(new KeyFrame(Duration.millis(250), //
				new KeyValue(scaleCurrent.xProperty(), 1.5), //
				new KeyValue(scaleCurrent.yProperty(), 1.5), new KeyValue(selectedContact.getLayout().opacityProperty(), 0))//
				, new KeyFrame(Duration.millis(70), //
						new KeyValue(chatViewScrollPane.translateYProperty(), -15), //
						new KeyValue(chatViewScrollPane.opacityProperty(), 0)));
		//once main/hiding animation is finished reset list element to normal and show new messages with animation
		scaleAnim.setOnFinished(e -> {
			chatView.getChildren().clear();
			scaleCurrent.setX(1);
			scaleCurrent.setY(1);
			selectedContact.getLayout().setOpacity(1);
			addMessagesAtTop(bsMsgs);
			chatViewScrollPane.setTranslateY(15);
			showMsgs.play();
			//allow clicking on new chat elements again
			switchingBlocked = false;
		});
		//apply scale transform to chat panel
		selectedContact.getLayout().getTransforms().add(scaleCurrent);
		//play all the animations
		scaleAnim.play();
		previousMsg = null;
	}

	/**
	 * Adds multiple message at the top of the chat
	 * 
	 * @param messages
	 */
	public void addMessagesAtTop(MessageFx[] messages) {
		addMessages(0, messages);
	}

	/**
	 * Adds one message at the bottom
	 * 
	 * @param message
	 */
	public void addMessageAtBottom(MessageFx message) {
		addMessages(chatView.getChildren().size(), new MessageFx[] { message });
		//scroll down after adding new message
		if (chatViewScrollPane.getVvalue() == 1) {
			PauseTransition wait = new PauseTransition(Duration.millis(100));
			wait.setOnFinished((e) -> chatViewScrollPane.setVvalue(1));
			wait.play();
		}
	}

	/**
	 * Adds messages from an array to the current chat </br>
	 * Adds new messages at the top. So to add messages the newest has to be the first and
	 * the oldest the last.
	 * 
	 * @param pos
	 * @param messages
	 */
	private void addMessages(int pos, MessageFx[] messages) {
		if (messages.length > 0 && !messages[0].isNormal()) {
			chatView.getChildren().add(messages[0].getPrimaryLayout());
			chatViewScrollPane.widthProperty().addListener(messages[0].getListener());
		} else {
			//add messages and date separators when they have been sent on different days
			for (MessageFx msg : messages) {
				if (previousMsg == null) {
					chatView.getChildren().add(pos, msg.getPrimaryLayout());
					previousMsg = msg;
					continue;
				}
				if (olderThan1Day(previousMsg, msg))
					chatView.getChildren().add(pos, new ChatDateInoFx(msg.getDate()).getLayout());
				chatView.getChildren().add(pos, msg.getPrimaryLayout());
				chatViewScrollPane.widthProperty().addListener(msg.getListener());

				previousMsg = msg;
			}

			if (previousMsg != null && chatView.getChildren().size() == 0)
				chatView.getChildren().add(pos, new ChatDateInoFx(previousMsg.getDate()).getLayout());
		}
	}

	/**
	 * Compare 2 message and return true if the newer message has been sent at least one
	 * day later
	 * 
	 * @param prevMsg
	 * @param newMsg
	 * @return true if the newer message has been sent at least one day later otherwise
	 * false
	 */
	private boolean olderThan1Day(MessageFx prevMsg, MessageFx newMsg) {
		Calendar prevMsgCal = Calendar.getInstance();
		Calendar newMsgCal = Calendar.getInstance();
		prevMsgCal.setTime(prevMsg.getDate());
		newMsgCal.setTime(newMsg.getDate());
		if (prevMsg.getDate().toString().substring(0, 10).equals(newMsg.getDate().toString().substring(0, 10)))
			return false;
		else if (prevMsgCal.get(Calendar.YEAR) > newMsgCal.get(Calendar.YEAR))
			return true;
		else if (prevMsgCal.get(Calendar.DAY_OF_YEAR) > newMsgCal.get(Calendar.DAY_OF_YEAR))
			return true;

		return false;
	}

	/**
	 * update the life message with new content
	 * 
	 * @param contentJson
	 */
	public void msgPart(String contentJson) {
		Message tmpMsgPart = gson.fromJson(contentJson, Message.class);
		if (tmpMsgPart.sender.equals(selectedChatContact)) {
			if (writingMsg == null) {
				writingMsg = new MessageFx(chatViewScrollPane.getWidth());
				Platform.runLater(() -> addMessageAtBottom(writingMsg));
			}
			writingMsg.updateWriting(tmpMsgPart.content);
		}
	}

	/**
	 * Send a message to the server/other contact
	 * 
	 * @param event
	 */
	public void sendMessage(MouseEvent event) {
		if (event.getButton() != MouseButton.PRIMARY)
			return;
		String text = msgInp.getText();
		if (text.isEmpty())
			return;
		serverCommunication.setBlocking(true);
		try {
			serverCommunication.write("sendMsg");
			serverCommunication.write(new Message(text, System.currentTimeMillis(), nameTitle.getText(), selectedChatContact, true));
		} catch (IOException e) {
			showInfoDialogue(e.getMessage());
			if (Boolean.parseBoolean(Info.getArgs()[0]))
				e.printStackTrace();
		}
		serverCommunication.setBlocking(false);
		addMessageAtBottom(new MessageFx(msgInp.getText(), true, new Date(), chatViewScrollPane.getWidth()));
		//place life message at the bottom again after the new message has been added
		if (writingMsg != null) {
			chatView.getChildren().remove(writingMsg.getPrimaryLayout());
			chatView.getChildren().add(writingMsg.getPrimaryLayout());
		}
		//reset text inputs
		msgInp.textProperty().removeListener(textInpListener);
		msgInp.clear();
		msgInp.textProperty().addListener(textInpListener);
	}

	/**
	 * Removes a life message
	 */
	public void removeLiveMessage() {
		if (writingMsg != null) {
			writingMsg.stopAnim();
			Platform.runLater(() -> {
				chatView.getChildren().remove(writingMsg.getPrimaryLayout());
				writingMsg = null;
			});
		}
	}

	/**
	 * Add a message either to the current chat or increment the notification counter
	 * 
	 * @param msg Message
	 */
	public void displayMsg(Message msg) {
		Platform.runLater(() -> {
			removeLiveMessage();
			if (msg.sender.equals(selectedChatContact)) {
				addMessageAtBottom(new MessageFx(//
						msg.content, //
						msg.sender.equals(nameTitle.getText()), //
						new Date(msg.date), //
						chatViewScrollPane.getWidth()));
			} else {
				contacts.get(contactsIndex.get(msg.sender)).increaseNewMsgCounter();
			}
		});
	}

	/**
	 * Change the image of the online indicator
	 * 
	 * @param state
	 */
	public void setOnlineStatus(String state) {
		onlineStatusImg.setVisible(true);
		switch (state) {
			case "false":
				onlineStatusImg.setImage(offline);
				break;
			case "true":
				onlineStatusImg.setImage(online);
				break;
			default:
				onlineStatusImg.setImage(statusUnknown);
				break;
		}
	}

	/**
	 * Closes the info dialogue pop up
	 * 
	 * @param event
	 */
	public void closeInfoDialogue(ActionEvent event) {
		TranslateTransition hide = new TranslateTransition(Duration.millis(200), infoDialogue);
		hide.setFromY(0);
		hide.setToY(infoDialogue.getHeight());
		hide.play();
	}

	/**
	 * Show the info dialogue with a text message
	 * 
	 * @param msg Text message
	 */
	public void showInfoDialogue(String msg) {
		if (Platform.isFxApplicationThread()) {
			((Text) infoDialogue.getChildren().get(0)).setText(msg);
			TranslateTransition show = new TranslateTransition(Duration.millis(200), infoDialogue);
			show.setFromY(infoDialogue.getHeight());
			show.setToY(0);
			show.play();
		} else {
			Platform.runLater(() -> {
				((Text) infoDialogue.getChildren().get(0)).setText(msg);
				TranslateTransition show = new TranslateTransition(Duration.millis(200), infoDialogue);
				show.setFromY(infoDialogue.getHeight());
				show.setToY(0);
				show.play();
			});
		}

	}

	/**
	 * testing method
	 * 
	 * @param event
	 */
	public void test1(ActionEvent event) {
		System.out.println(2);
	}

	/**
	 * testing method
	 * 
	 * @param event
	 */
	public void buttonTest(MouseEvent event) {
		MessageFx mFx = new MessageFx(chatViewScrollPane.getWidth());
		addMessageAtBottom(mFx);
		mFx.updateWriting("byee");
		writingMsg = mFx;
	}

	/**
	 * Display the name of the current user
	 * 
	 * @param name The name of the current user
	 */
	public void setNameTitle(String name) {

		nameTitle.setText(name);
	}

	/**
	 * @param cliServComm The class that handles the server communication
	 */
	public void setComm(ClientSideToServer cliServComm) {
		serverCommunication = cliServComm;
	}

	/**
	 * @return The width of the current scroll pane
	 */
	public double getScrollPaneWidth() {
		return chatViewScrollPane.getWidth();
	}

	/**
	 * @return VBox that holds all the messages of the current chat
	 */
	public VBox getChatView() {
		return chatView;
	}

	/**
	 * @return ScrollPane that holds all messages from the current chat
	 */
	public ScrollPane getChatViewScrollPane() {
		return chatViewScrollPane;
	}

	/**
	 * Display to profile image of the current user
	 * 
	 * @param img
	 */
	public void setProfilePic(Image img) {
		userProfilePic.setImage(img);
	}

}
