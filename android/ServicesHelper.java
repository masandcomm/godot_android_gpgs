package org.godotengine.godot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.InvitationsClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public class ServicesHelper {

    private final String LOG_TAG = "ServicesHelper";

    private final int RC_SING_IN = 5001;
    private final int RC_ACHIEVEMENT_UI = 9001;
    private final int RC_SELECT_PLAYERS = 9006;
    private final int RC_WAITING_ROOM = 9007;
    private final int RC_INVITATION_INBOX = 9008;

    private Activity activity;
    private Context context;

    private AchievementsClient achievementsClient;
    private LeaderboardsClient leaderboardsClient;

    private PlayersClient playersClient;
    private RealTimeMultiplayerClient realTimeMultiplayerClient;
    private InvitationsClient invitationsClient;
    private GamesClient gamesClient;
    private GoogleSignInClient signInClient;


    private Invitation invitation;
    private String localPlayerId = null;
    private ArrayList<Participant> mParticipants = null;
    private Room mRoom = null;
    private byte[] mMsgBuf = new byte[3];
    private RoomConfig mJoinedRoomConfig;

    private BackMessageListener messageListener;
    private OnRealTimeMessageReceivedListener mMessageReceivedHandler = new OnRealTimeMessageReceivedListener() {
        @Override
        public void onRealTimeMessageReceived(@NonNull RealTimeMessage rtm) {
            byte[] buf = rtm.getMessageData();
            String sender = rtm.getSenderParticipantId();
            propagate("rt_multiplayer", "message_received");
        }
    };
    private RoomUpdateCallback mRoomUpdateCallback = new RoomUpdateCallback() {
        @Override
        public void onRoomCreated(int code, @Nullable Room room) {
            //From docs: Called when the client attempts to create a real-time room.
            // Update UI and internal state based on room updates.
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(LOG_TAG, "Room " + room.getRoomId() + " created.");
            } else {
                Log.w(LOG_TAG, "Error creating room: " + code);
            }
        }

        @Override
        public void onJoinedRoom(int code, @Nullable Room room) {
            // From docs: Called when the client attempts to join a real-time room.
            // Update UI and internal state based on room updates.
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(LOG_TAG, "Room " + room.getRoomId() + " joined.");
            } else {
                Log.w(LOG_TAG, "Error joining room: " + code);
            }
        }

        @Override
        public void onLeftRoom(int code, @NonNull String roomId) {
            // From docs: Called when the client attempts to leaves the real-time room.
            Log.d(LOG_TAG, "Left room" + roomId);
            // we have left the room; return to main screen.
            Log.d(LOG_TAG, "onLeftRoom, code " + code);
            // we have left the room; return to main screen.
            Log.d(LOG_TAG, "onLeftRoom, code " + code);
            mParticipants = null;
        }

        @Override
        public void onRoomConnected(int code, @Nullable Room room) {
            // From docs: Called when all the participants in a real-time room are fully connected.
            if (code == GamesCallbackStatusCodes.OK && room != null) {
                Log.d(LOG_TAG, "Room " + room.getRoomId() + " connected.");
            } else {
                Log.w(LOG_TAG, "Error connecting to room: " + code);
            }
        }
    };
    private RoomStatusUpdateCallback mRoomStatusCallbackHandler = new RoomStatusUpdateCallback() {
        @Override
        public void onRoomConnecting(@Nullable Room room) {
            //From docs: Called when one or more participants have joined the room and have started the process of establishing peer connections.
            // Update the UI status since we are in the process of connecting to a specific room.
        }

        @Override
        public void onRoomAutoMatching(@Nullable Room room) {
            // From docs: Called when the server has started the process of auto-matching.
            // Update the UI status since we are in the process of matching other players.
        }

        @Override
        public void onPeerInvitedToRoom(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peers are invited to a room.
            // Update the UI status since we are in the process of matching other players.
        }

        @Override
        public void onPeerDeclined(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peers decline the invitation to a room.
        }

        @Override
        public void onPeerJoined(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peer participants join a room.
            // Update UI status indicating new players have joined!
        }

        @Override
        public void onPeerLeft(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peer participant leave a room.
        }

        @Override
        public void onConnectedToRoom(@Nullable Room room) {
            // From docs: Called when the client is connected to the connected set in a room.
        }

        @Override
        public void onDisconnectedFromRoom(@Nullable Room room) {
            // From docs: Called when the client is disconnected from the connected set in a room.
        }

        @Override
        public void onPeersConnected(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peer participants are connected to a room.

        }

        @Override
        public void onPeersDisconnected(@Nullable Room room, @NonNull List<String> participantIds) {
            // From docs: Called when one or more peer participants are disconnected from a room.
        }

        @Override
        public void onP2PConnected(@NonNull String participantId) {
            // From docs: Called when the client is successfully connected to a peer participant.
            // Update status due to new peer to peer connection.
        }

        @Override
        public void onP2PDisconnected(@NonNull String participantId) {
            // From docs: Called when client gets disconnected from a peer participant.
            // Update status due to  peer to peer connection being disconnected.
        }

    };
    private InvitationCallback invitationCallback = new InvitationCallback() {
        // Called when we get an invitation to play a game. We react by showing that to the user.
        @Override
        public void onInvitationReceived(@NonNull Invitation invitation) {
            // We got an invitation to play a game! So, store it in
            // mIncomingInvitationId
            // and show the popup on the screen.
            String invitationId = invitation.getInvitationId();
            propagate("rt_multiplayer", "invitation_received");
        }

        @Override
        public void onInvitationRemoved(@NonNull String invitationId) {
            propagate("rt_multiplayer", "invitation_removed");
        }
    };

    public ServicesHelper(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    private void onConnected() {

        Log.d(LOG_TAG, "onConnected() called. Setting invitation listener ...");

        getInvitationsClient().registerInvitationCallback(invitationCallback);
        checkForInvitation();

        Log.d(LOG_TAG, "Clients initialized");

        propagate("services", "connected");
    }

    private void onDisconnected() {
        Log.d(LOG_TAG, "onDisconnected() called.");
        achievementsClient = null;
        leaderboardsClient = null;
        playersClient = null;
        realTimeMultiplayerClient = null;
        invitationsClient = null;
        gamesClient = null;
    }

    public void signIn() {
        Intent intent = getSignInClient().getSignInIntent();
        activity.startActivityForResult(intent, RC_SING_IN);
    }

    public void signOut() {
        Log.d(LOG_TAG, "signOut()");

        if (!isSignedIn()) {
            Log.w(LOG_TAG, "signOut() called, but was not signed in!");
            return;
        }
        getSignInClient().signOut().addOnCompleteListener(activity, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                boolean successful = task.isSuccessful();
                Log.d(LOG_TAG, "signOut(): " + (successful ? "success" : "failed"));

                onDisconnected();
            }
        });

    }

    public void signInSilently() {
        Log.d(LOG_TAG, "Silently: Sign in ...");
        getSignInClient().silentSignIn().addOnCompleteListener(activity,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            onConnected();
                        } else {
                            Log.d(LOG_TAG, "Silently: Unsuccessful sign in.", task.getException());
                        }
                    }
                });

    }

    public boolean isSignedIn() {

        return getGoogleAccount() != null;
    }

    public void onResume() {
        Log.d(LOG_TAG, "Starting silent sign in.");
        signInSilently();
    }

    public void onPause() {

        if (isSignedIn()) {
            Log.d(LOG_TAG, "onPause called. Removing invitation listener.");
            getInvitationsClient().unregisterInvitationCallback(invitationCallback);
        }

    }

    public void onActivityResult(int requestCode, int responseCode, Intent intent) {

        switch (requestCode) {

            case RC_SING_IN:

                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
                if (result.isSuccess()) {
                    Log.d(LOG_TAG, "Connected from Sing In Activity");
                    onConnected();
                } else {
                    String message = result.getStatus().getStatusMessage();
                    if (message == null || message.isEmpty()) {
                        message = "Response code: " + responseCode;
                    }
                    Log.d(LOG_TAG, "Login error: " + message);
                }
                break;

            case RC_SELECT_PLAYERS:
                if (responseCode != Activity.RESULT_OK) {
                    Log.w(LOG_TAG, "*** select players UI cancelled, " + responseCode);
                    // switchToMainScreen();
                } else {
                    handleSelectPlayers(intent);
                }
                break;

            case RC_INVITATION_INBOX:
                if (responseCode != Activity.RESULT_OK) {
                    Log.w(LOG_TAG, "*** invitation inbox UI cancelled, " + responseCode);
                    // switchToMainScreen();
                    return;
                }

                Log.d(LOG_TAG, "Invitation inbox UI succeeded.");
                Invitation inv = intent.getExtras().getParcelable(
                        Multiplayer.EXTRA_INVITATION);

                // accept invitation
                acceptInviteToRoom(inv.getInvitationId());
                break;

            case RC_WAITING_ROOM:
                // we got the result from the "waiting room" UI.
                if (responseCode == Activity.RESULT_OK) {
                    // ready to start playing
                    Log.d("MultiplayerManager",
                            "Starting game (waiting room returned OK).");
                    startGame();
                } else if (responseCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                    leaveRoom();
                } else if (responseCode == Activity.RESULT_CANCELED) {
                    leaveRoom();
                }
                break;

        }
    }

    public void showAchievements() {
        if (isSignedIn()) {
            getAchievementsClient().getAchievementsIntent().addOnSuccessListener(new OnSuccessListener<Intent>() {
                @Override
                public void onSuccess(Intent intent) {
                    activity.startActivityForResult(intent, RC_ACHIEVEMENT_UI);
                }
            });
        } else {
            signIn();
        }

    }

    public void unlockAchievement(final String achievementId) {

        if (isSignedIn()) {
            getAchievementsClient().unlock(achievementId);
        } else {
            Log.d(LOG_TAG, "Achievements: Client is not connected. Unlock failed.");
        }

    }

    public void incrementAchievement(final String achievementId, final int incrementStep) {

        if (isSignedIn()) {
            getAchievementsClient().increment(achievementId, incrementStep);
        } else {
            Log.d(LOG_TAG, "Achievements: Client is not connected. "
                    + "Achievement increment failed.");
        }

    }

    public void submitScore(final String leaderboardId, final long score) {

        if (isSignedIn()) {
            getLeaderboardsClient().submitScore(leaderboardId, score);
        } else {
            Log.d(LOG_TAG, "Leaderboards: Client is not connected. Submit score failed.");
        }

    }

    public void showLeaderboard(final String leaderboardId) {

        if (isSignedIn()) {
            getLeaderboardsClient().getLeaderboardIntent(leaderboardId);
        } else {
            Log.d(LOG_TAG,
                    "Leaderboards: Client is not connected. Cannot show leaderboard: "
                            + leaderboardId);
            signIn();
        }

    }

    public void invitePlayers() {

        if (isSignedIn()) {
            // launch the player selection screen
            // minimum: 1 other player; maximum: 3 other players
            getRealTimeMultiplayerClient()
                    .getSelectOpponentsIntent(1, 3, true)
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            activity.startActivityForResult(intent, RC_SELECT_PLAYERS);
                        }
                    });
        } else {
            signIn();
        }

    }

    public void showInvitations() {

        if (isSignedIn()) {
            getInvitationsClient()
                    .getInvitationInboxIntent()
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            activity.startActivityForResult(intent, RC_INVITATION_INBOX);
                        }
                    });

        } else {
            signIn();
        }

    }

    public boolean hasInvitation() {
        if (isSignedIn()) {
            //TODO Add invitiation receiving logic
            return invitation != null;
        }
        return false;
    }

    public void startQuickGame(final int role) {

        if (isSignedIn()) {
            final int MIN_OPPONENTS = 1, MAX_OPPONENTS = 1;
            Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS, MAX_OPPONENTS, role);

            // build the room config:
            RoomConfig roomConfig =
                    RoomConfig.builder(mRoomUpdateCallback)
                            .setOnMessageReceivedListener(mMessageReceivedHandler)
                            .setRoomStatusUpdateCallback(mRoomStatusCallbackHandler)
                            .setAutoMatchCriteria(autoMatchCriteria)
                            .build();

            // Save the roomConfig so we can use it if we call leave().
            mJoinedRoomConfig = roomConfig;

            // create room:
            getRealTimeMultiplayerClient().create(roomConfig);

            propagate("rt_multiplayer", "creating_room");
        } else {
            signIn();
        }

    }

    private void checkForInvitation() {
        getGamesClient().getActivationHint()
                .addOnSuccessListener(
                        new OnSuccessListener<Bundle>() {
                            @Override
                            public void onSuccess(Bundle bundle) {
                                if (bundle != null) {
                                    invitation = bundle.getParcelable(Multiplayer.EXTRA_INVITATION);
                                    if (invitation != null) {
                                        acceptInviteToRoom(invitation.getInvitationId());
                                    }
                                }
                            }
                        }
                );
    }

    private void showWaitingRoom(Room room, int maxPlayersToStartGame) {
        getRealTimeMultiplayerClient().getWaitingRoomIntent(room, maxPlayersToStartGame)
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_WAITING_ROOM);
                    }
                });
    }

    public void handleSelectPlayers(Intent data) {

        Log.d(LOG_TAG, "Select players UI succeeded.");

        // Get the invitee list.
        final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

        // Get Automatch criteria.
        int minAutoPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
        int maxAutoPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

        // Create the room configuration.
        RoomConfig.Builder roomBuilder = RoomConfig.builder(mRoomUpdateCallback)
                .setOnMessageReceivedListener(mMessageReceivedHandler)
                .setRoomStatusUpdateCallback(mRoomStatusCallbackHandler)
                .addPlayersToInvite(invitees);
        if (minAutoPlayers > 0) {
            roomBuilder.setAutoMatchCriteria(
                    RoomConfig.createAutoMatchCriteria(minAutoPlayers, maxAutoPlayers, 0));
        }

        // Save the roomConfig so we can use it if we call leave().
        mJoinedRoomConfig = roomBuilder.build();
        getRealTimeMultiplayerClient().create(mJoinedRoomConfig);

        Log.d(LOG_TAG, "Room created, waiting for it to be ready...");
        propagate("rt_multiplayer", "creating_room");
    }

    public void acceptInviteToRoom(String invId) {

        Log.d(LOG_TAG, "Accepting invitation: " + invId);
        RoomConfig.Builder builder = RoomConfig.builder(mRoomUpdateCallback)
                .setInvitationIdToAccept(invId);
        mJoinedRoomConfig = builder.build();
        getRealTimeMultiplayerClient().join(mJoinedRoomConfig);
        propagate("rt_multiplayer", "joining_room");
    }

    public void leaveRoom() {
        Log.d(LOG_TAG, "Leaving room.");
        if (mRoom.getRoomId() != null) {
            getRealTimeMultiplayerClient().leave(mJoinedRoomConfig, mRoom.getRoomId());
            mRoom = null;
        }
        propagate("rt_multiplayer", "leaving_room");
    }

    public void startGame() {

        propagate("rt_multiplayer", "start_game");
    }

    public void broadcastMessage(int parentTag, int targetTag, int lastValue) {

        mMsgBuf[0] = (byte) parentTag;
        mMsgBuf[1] = (byte) targetTag;
        mMsgBuf[2] = (byte) lastValue;

       /* myScore = myScore + lastValue;
        scores.put(mMyId, myScore);
        // Send to every other participant.
        for (Participant p : mParticipants) {
            if (p.getParticipantId().equals(mMyId)) {
                continue;
            }
            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;

            }
            if (mRoom != null) {
                Games.RealTimeMultiplayer.sendUnreliableMessage(
                        activity.mHelper.getApiClient(), mMsgBuf, mRoom.getRoomId(),
                        p.getParticipantId());
            }*/
    }

    private void propagate(String from, String what) {
        messageListener.propagate(from, what);
    }

    public void isConnected() {
        if (isSignedIn()) {
            propagate("connected", "yes");
        } else {
            propagate("connected", "no");
        }
    }

    private GoogleSignInAccount getGoogleAccount() {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    private GoogleSignInClient getSignInClient() {
        if (signInClient == null) {
            signInClient = GoogleSignIn.getClient(activity,
                    GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        }
        return signInClient;
    }

    private AchievementsClient getAchievementsClient() {
        if (achievementsClient == null) {
            achievementsClient = Games.getAchievementsClient(context, getGoogleAccount());
        }
        return achievementsClient;
    }

    private LeaderboardsClient getLeaderboardsClient() {
        if (leaderboardsClient == null) {
            leaderboardsClient = Games.getLeaderboardsClient(context, getGoogleAccount());
        }
        return leaderboardsClient;
    }

    private PlayersClient getPlayersClient() {
        if (playersClient == null) {
            playersClient = Games.getPlayersClient(context, getGoogleAccount());
        }
        return playersClient;
    }

    private RealTimeMultiplayerClient getRealTimeMultiplayerClient() {
        if (realTimeMultiplayerClient == null) {
            realTimeMultiplayerClient = Games.getRealTimeMultiplayerClient(context, getGoogleAccount());
        }
        return realTimeMultiplayerClient;
    }

    private InvitationsClient getInvitationsClient() {
        if (invitationsClient == null) {
            invitationsClient = Games.getInvitationsClient(context, getGoogleAccount());
        }
        return invitationsClient;
    }

    private GamesClient getGamesClient() {
        if (gamesClient == null) {
            gamesClient = Games.getGamesClient(context, getGoogleAccount());
        }
        return gamesClient;
    }

    public void setMessageListener(BackMessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public interface BackMessageListener {
        void propagate(String from, String what);
    }
}