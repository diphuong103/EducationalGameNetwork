package com.edugame.common;

/**
 * Protocol class defines message types and constants for client-server communication
 */
public class Protocol {

    // Authentication
    public static final String LOGIN = "LOGIN";
    public static final String REGISTER = "REGISTER";
    public static final String LOGOUT = "LOGOUT";

    // Game
    public static final String ANSWER_QUESTION = "ANSWER_QUESTION";
    public static final String GAME_RESULT = "GAME_RESULT";
    public static final String NEXT_QUESTION = "NEXT_QUESTION";

    public static final String UPDATE_PASSWORD= "UPDATE_PASSWORD";
    public static final String GET_STATISTICS = "GET_STATISTICS";
    public static final String GET_TRAINING_QUESTIONS = "GET_TRAINING_QUESTIONS";
    public static final String SUBMIT_ANSWER = "SUBMIT_ANSWER";

    public static final String TRAINING_MODE = "TRAINING_MODE";
    public static final String MATCH_FAILED = "MATCH_FAILED";


    // Room -protocol
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String JOIN_ROOM_RESPONSE = "JOIN_ROOM_RESPONSE";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String START_GAME = "START_GAME";
    public static final String READY = "READY";
    public static final String ROOM_UPDATE = "ROOM_UPDATE";
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String PLAYER_READY = "PLAYER_READY";
    public static final String KEY_ROOM_ID = "room_id";
    public static final String ROOM_CHAT = "ROOM_CHAT";
    public static final String INVITE_TO_ROOM = "INVITE_TO_ROOM";
    public static final String KICK_PLAYER = "KICK_PLAYER";



    // Chat
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";

    public static final String PRIVATE_MESSAGE = "PRIVATE_MESSAGE";

    public static final String GET_MESSAGES = "GET_MESSAGES";
    public static final String GET_MESSAGES_RESPONSE = "GET_MESSAGES_RESPONSE";

    public static final String SEND_MESSAGE = "SEND_MESSAGE";
    public static final String SEND_MESSAGE_RESPONSE = "SEND_MESSAGE_RESPONSE";

    public static final String NEW_MESSAGE = "NEW_MESSAGE";

    public static final String MESSAGE_READ = "MESSAGE_READ";
    public static final String MESSAGE_READ_STATUS = "MESSAGE_READ_STATUS";

    public static final String GET_UNREAD_COUNT = "GET_UNREAD_COUNT";
    public static final String GET_UNREAD_COUNT_RESPONSE = "GET_UNREAD_COUNT_RESPONSE";

    public static final String GLOBAL_CHAT = "GLOBAL_CHAT";
    public static final String GAME_CHAT = "GAME_CHAT";



    // Friends
    public static final String SEARCH_USERS = "SEARCH_USERS";
    public static final String ADD_FRIEND = "ADD_FRIEND";
    public static final String ACCEPT_FRIEND = "ACCEPT_FRIEND";
    public static final String REJECT_FRIEND = "REJECT_FRIEND";
    public static final String REMOVE_FRIEND = "REMOVE_FRIEND";
    public static final String GET_FRIENDS_LIST = "GET_FRIENDS_LIST";
    public static final String GET_PENDING_REQUESTS = "GET_PENDING_REQUESTS";
    public static final String CHECK_FRIENDSHIP_STATUS = "CHECK_FRIENDSHIP_STATUS";



    // Leaderboard
    public static final String GET_LEADERBOARD = "GET_LEADERBOARD";

    // Profile
    public static final String GET_PROFILE = "GET_PROFILE";
    public static final String UPDATE_PROFILE = "UPDATE_PROFILE";
    public static final String GET_PROFILE_BY_ID = "GET_PROFILE_BY_ID";

    public static final String GET_PROFILE_BY_ID_RESPONSE = "GET_PROFILE_BY_ID_RESPONSE";

    public static final String INVITE_FRIEND = "INVITE_FRIEND";
    public static final String ACCEPT_INVITE = "ACCEPT_INVITE";
    public static final String REJECT_INVITE = "REJECT_INVITE";

    public static final String FIND_MATCH = "FIND_MATCH";
    public static final String CANCEL_FIND_MATCH = "CANCEL_FIND_MATCH";
    public static final String MATCH_FOUND = "MATCH_FOUND";

    // Response Messages
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";

    // Game States
    public static final String WAITING = "WAITING";
    public static final String PLAYING = "PLAYING";
    public static final String FINISHED = "FINISHED";

    // Subjects
    public static final String MATH = "math";
    public static final String ENGLISH = "english";
    public static final String LITERATURE = "literature";

    // Difficulty Levels
    public static final String EASY = "easy";
    public static final String MEDIUM = "medium";
    public static final String HARD = "hard";

    // Default Values
    public static final int DEFAULT_PORT = 8888;
    public static final int MAX_PLAYERS_PER_ROOM = 4;
    public static final int QUESTIONS_PER_GAME = 10;
    public static final int QUESTION_TIME_LIMIT = 30; // seconds

    // Score Points
    public static final int EASY_POINTS = 10;
    public static final int MEDIUM_POINTS = 20;
    public static final int HARD_POINTS = 30;
    public static final int SPEED_BONUS_MULTIPLIER = 2;
}