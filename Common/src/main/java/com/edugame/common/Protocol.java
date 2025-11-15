package com.edugame.common;

/**
 * Protocol class defines message types and constants for client-server communication
 * Used in EduGame platform for multiplayer math racing and social interaction.
 */
public class Protocol {

    // ============================================
    // AUTHENTICATION
    // ============================================
    public static final String LOGIN = "LOGIN";
    public static final String REGISTER = "REGISTER";
    public static final String LOGOUT = "LOGOUT";

    // ============================================
    // ROOM MANAGEMENT
    // ============================================
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String JOIN_ROOM_RESPONSE = "JOIN_ROOM_RESPONSE";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String ROOM_UPDATE = "ROOM_UPDATE";
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String PLAYER_READY = "PLAYER_READY";
    public static final String READY = "READY";
    public static final String INVITE_TO_ROOM = "INVITE_TO_ROOM";
    public static final String KICK_PLAYER = "KICK_PLAYER";
    public static final String ROOM_CHAT = "ROOM_CHAT";
    public static final String JOIN_GAME = "JOIN_GAME";
    public static final String KEY_ROOM_ID = "room_id";

    // ============================================
    // GAME PROTOCOL - MATH RACER
    // ============================================
    public static final String START_GAME = "START_GAME";
    public static final String GAME_QUESTION = "GAME_QUESTION";
    public static final String NEXT_QUESTION = "NEXT_QUESTION";
    public static final String SUBMIT_ANSWER = "SUBMIT_ANSWER";
    public static final String ANSWER_QUESTION = "ANSWER_QUESTION"; // alias
    public static final String ANSWER_RESULT = "ANSWER_RESULT";
    public static final String GAME_UPDATE = "GAME_UPDATE";
    public static final String PLAYER_POSITION_UPDATE = "PLAYER_POSITION_UPDATE";
    public static final String QUESTION_RESULT = "QUESTION_RESULT" ;
    public static final String NITRO_BOOST = "NITRO_BOOST";
    public static final String GAME_END = "GAME_END";
    public static final String GAME_RESULT = "GAME_RESULT"; // alias
    public static final String GAME_PAUSED = "GAME_PAUSED";
    public static final String GAME_RESUMED = "GAME_RESUMED";
    public static final String LEAVE_GAME = "LEAVE_GAME";
    public static final String READY_NEXT_QUESTION = "READY_NEXT_QUESTION";
    public static final String GET_GAME_STATE = "GET_GAME_STATE";
    public static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";
    public static final String GET_STATISTICS = "GET_STATISTICS";
    public static final String GET_TRAINING_QUESTIONS = "GET_TRAINING_QUESTIONS";
    public static final String PLAYER_ANSWERED = "PLAYER_ANSWERED";
    public static final String PLAYER_PROGRESS = "PLAYER_PROGRESS";

    // ==================== SUBJECTS ====================

    public static final String SUBJECT_MATH = "MATH";
    public static final String SUBJECT_ENGLISH = "ENGLISH";
    public static final String SUBJECT_LITERATURE = "LITERATURE";


    public static final String[] SUBJECTS = {
            SUBJECT_MATH,
            SUBJECT_ENGLISH,
            SUBJECT_LITERATURE,
    };

    // ==================== DIFFICULTY LEVELS ====================

    public static final String DIFFICULTY_EASY = "EASY";
    public static final String DIFFICULTY_MEDIUM = "MEDIUM";
    public static final String DIFFICULTY_HARD = "HARD";

    public static final String[] DIFFICULTIES = {
            DIFFICULTY_EASY,
            DIFFICULTY_MEDIUM,
            DIFFICULTY_HARD
    };


    public static final Object TRAINING_MODE = "TRAINING_MODE" ;
    // ============================================
    // CHAT SYSTEM
    // ============================================
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String PRIVATE_MESSAGE = "PRIVATE_MESSAGE";
    public static final String GLOBAL_CHAT = "GLOBAL_CHAT";
    public static final String GAME_CHAT = "GAME_CHAT";
    public static final String SEND_MESSAGE = "SEND_MESSAGE";
    public static final String SEND_MESSAGE_RESPONSE = "SEND_MESSAGE_RESPONSE";
    public static final String NEW_MESSAGE = "NEW_MESSAGE";
    public static final String GET_MESSAGES = "GET_MESSAGES";
    public static final String GET_MESSAGES_RESPONSE = "GET_MESSAGES_RESPONSE";
    public static final String MESSAGE_READ = "MESSAGE_READ";
    public static final String MESSAGE_READ_STATUS = "MESSAGE_READ_STATUS";
    public static final String GET_UNREAD_COUNT = "GET_UNREAD_COUNT";
    public static final String GET_UNREAD_COUNT_RESPONSE = "GET_UNREAD_COUNT_RESPONSE";

    // ============================================
    // FRIEND SYSTEM
    // ============================================
    public static final String SEARCH_USERS = "SEARCH_USERS";
    public static final String ADD_FRIEND = "ADD_FRIEND";
    public static final String ACCEPT_FRIEND = "ACCEPT_FRIEND";
    public static final String REJECT_FRIEND = "REJECT_FRIEND";
    public static final String REMOVE_FRIEND = "REMOVE_FRIEND";
    public static final String GET_FRIENDS_LIST = "GET_FRIENDS_LIST";
    public static final String GET_PENDING_REQUESTS = "GET_PENDING_REQUESTS";
    public static final String CHECK_FRIENDSHIP_STATUS = "CHECK_FRIENDSHIP_STATUS";

    // ============================================
    // PROFILE SYSTEM
    // ============================================
    public static final String GET_PROFILE = "GET_PROFILE";
    public static final String UPDATE_PROFILE = "UPDATE_PROFILE";
    public static final String GET_PROFILE_BY_ID = "GET_PROFILE_BY_ID";
    public static final String GET_PROFILE_BY_ID_RESPONSE = "GET_PROFILE_BY_ID_RESPONSE";
    public static final String INVITE_FRIEND = "INVITE_FRIEND";
    public static final String ACCEPT_INVITE = "ACCEPT_INVITE";
    public static final String REJECT_INVITE = "REJECT_INVITE";

    // ============================================
    // MATCHMAKING
    // ============================================
    public static final String FIND_MATCH = "FIND_MATCH";
    public static final String CANCEL_FIND_MATCH = "CANCEL_FIND_MATCH";
    public static final String MATCH_FOUND = "MATCH_FOUND";
    public static final String MATCH_FAILED = "MATCH_FAILED";

    // ============================================
    // LEADERBOARD
    // ============================================
    public static final String GET_LEADERBOARD = "GET_LEADERBOARD";

    // ============================================
    // RESPONSES & ERRORS
    // ============================================
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String RESPONSE = "RESPONSE";

    // ============================================
    // GAME STATES & CONSTANTS
    // ============================================
    public static final String WAITING = "WAITING";
    public static final String PLAYING = "PLAYING";
    public static final String FINISHED = "FINISHED";

    // Player States
    public static final String PLAYER_STATE_WAITING = "WAITING";
    public static final String PLAYER_STATE_READY = "READY";
    public static final String PLAYER_STATE_PLAYING = "PLAYING";
    public static final String PLAYER_STATE_FINISHED = "FINISHED";
    public static final String PLAYER_STATE_DISCONNECTED = "DISCONNECTED";

    // Room States
    public static final String ROOM_STATE_WAITING = "WAITING";
    public static final String ROOM_STATE_READY = "READY";
    public static final String ROOM_STATE_PLAYING = "PLAYING";
    public static final String ROOM_STATE_FINISHED = "FINISHED";

    // End Reasons
    public static final String END_REASON_FINISH = "FINISH_LINE";
    public static final String END_REASON_TIME_UP = "TIME_UP";
    public static final String END_REASON_ALL_QUIT = "ALL_QUIT";

    // Game Types
    public static final String GAME_TYPE_MATH = "MATH";
    public static final String GAME_TYPE_ENGLISH = "ENGLISH";
    public static final String GAME_TYPE_SCIENCE = "SCIENCE";

    // Subjects
    public static final String MATH = "math";
    public static final String ENGLISH = "english";
    public static final String LITERATURE = "literature";

    // Difficulty Levels
    public static final String EASY = "easy";
    public static final String MEDIUM = "medium";
    public static final String HARD = "hard";

    // ============================================
    // GAME MECHANICS
    // ============================================

    // Distances
    public static final int NITRO_DISTANCE = 100;    // Fastest answer
    public static final int NORMAL_DISTANCE = 60;    // Correct answer
    public static final int PENALTY_DISTANCE = -20;  // Timeout penalty
    public static final int FINISH_LINE = 1450;      // Finish line position

    // Timing
    public static final int QUESTION_TIME_LIMIT = 15;  // Seconds per question
    public static final int GAME_DURATION = 300;       // Seconds (5 minutes)
    public static final int MAX_QUESTIONS = 20;        // Per game

    // Scoring
    public static final int POINTS_NITRO = 100;
    public static final int POINTS_CORRECT = 60;
    public static final int POINTS_WRONG = 0;
    public static final int POINTS_TIMEOUT = -10;

    // Bonus Scoring by Difficulty
    public static final int EASY_POINTS = 10;
    public static final int MEDIUM_POINTS = 20;
    public static final int HARD_POINTS = 30;
    public static final int SPEED_BONUS_MULTIPLIER = 2;

    // ============================================
    // DEFAULT CONFIGURATION
    // ============================================
    public static final int DEFAULT_PORT = 8888;
    public static final int MAX_PLAYERS_PER_ROOM = 4;
    public static final int QUESTIONS_PER_GAME = 10;


    // Matchmaking
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int MATCHMAKING_TIMEOUT = 60;         // 60 seconds

    // Position & Movement (Race Track)
    public static final double START_POSITION = 0.0;


}
