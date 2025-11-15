package com.edugame.server.web;

import com.edugame.server.database.UserDAO;
import com.edugame.server.database.GameResultDAO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * ProfileHandler - Display player profile using UserDAO
 */
public class ProfileHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Parse user ID from URL: /profile?id=123
            String query = exchange.getRequestURI().getQuery();

            if (query == null || !query.startsWith("id=")) {
                sendError(exchange, "Missing user ID parameter");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(query.substring(3).split("&")[0]);
            } catch (NumberFormatException e) {
                sendError(exchange, "Invalid user ID");
                return;
            }

            // Get user data from UserDAO
            UserDAO userDAO = new UserDAO();
            UserDAO.PlayerInfo player = userDAO.getPlayerInfoById(userId);

            if (player == null) {
                sendError(exchange, "User not found");
                return;
            }

            // Get game history
            GameResultDAO gameResultDAO = new GameResultDAO();
            List<GameResultDAO.GameResult> history = gameResultDAO.getUserGameHistory(userId, 10);

            // Build HTML
            String html = buildProfilePage(player, history);

            WebServer.sendResponse(exchange, 200, html, "text/html");

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Database error: " + e.getMessage());
        }
    }

    private String buildProfilePage(UserDAO.PlayerInfo player,
                                    List<GameResultDAO.GameResult> history) {

        DecimalFormat df = new DecimalFormat("#.##");
        double winRate = player.getWinRate();
        String winRateStr = df.format(winRate);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"vi\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Profile - ").append(escapeHtml(player.username)).append("</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px 20px; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        .back-button { display: inline-block; background: rgba(255,255,255,0.2); color: white; padding: 10px 20px; border-radius: 25px; text-decoration: none; margin-bottom: 20px; transition: background 0.3s; }\n");
        html.append("        .back-button:hover { background: rgba(255,255,255,0.3); }\n");
        html.append("        .profile-header { background: white; border-radius: 20px; padding: 40px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); margin-bottom: 30px; display: flex; align-items: center; gap: 30px; }\n");
        html.append("        .avatar-large { width: 120px; height: 120px; border-radius: 50%; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center; color: white; font-size: 3em; font-weight: bold; flex-shrink: 0; overflow: hidden; position: relative; }\n");
        html.append("        .avatar-large img { width: 100%; height: 100%; object-fit: cover; position: absolute; top: 0; left: 0; }\n");
        html.append("        .avatar-letter { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }\n");
        html.append("        .profile-info { flex: 1; }\n");
        html.append("        .username { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }\n");
        html.append("        .fullname { font-size: 1.3em; color: #666; margin-bottom: 15px; }\n");
        html.append("        .profile-meta { display: flex; gap: 20px; flex-wrap: wrap; }\n");
        html.append("        .meta-item { display: flex; align-items: center; gap: 8px; color: #666; font-size: 0.95em; }\n");
        html.append("        .new-badge { display: inline-block; background: #4ade80; color: white; padding: 4px 12px; border-radius: 12px; font-size: 0.8em; margin-left: 10px; }\n");
        html.append("        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }\n");
        html.append("        .stat-card { background: white; border-radius: 15px; padding: 25px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); transition: transform 0.3s; }\n");
        html.append("        .stat-card:hover { transform: translateY(-5px); }\n");
        html.append("        .stat-icon { font-size: 2em; margin-bottom: 10px; }\n");
        html.append("        .stat-value { font-size: 2.2em; font-weight: bold; color: #667eea; margin-bottom: 5px; }\n");
        html.append("        .stat-label { color: #666; font-size: 0.95em; }\n");
        html.append("        .section { background: white; border-radius: 20px; padding: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); margin-bottom: 30px; }\n");
        html.append("        .section-title { font-size: 1.8em; color: #667eea; margin-bottom: 20px; display: flex; align-items: center; gap: 10px; }\n");
        html.append("        .progress-bar-container { margin-bottom: 20px; }\n");
        html.append("        .progress-label { display: flex; justify-content: space-between; margin-bottom: 8px; font-weight: 500; color: #333; }\n");
        html.append("        .progress-bar { width: 100%; height: 30px; background: #f0f0f0; border-radius: 15px; overflow: hidden; position: relative; }\n");
        html.append("        .progress-fill { height: 100%; background: linear-gradient(90deg, #667eea 0%, #764ba2 100%); border-radius: 15px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; transition: width 1s ease; }\n");
        html.append("        .history-table { width: 100%; border-collapse: collapse; }\n");
        html.append("        .history-table th { background: #f8f9fa; padding: 15px; text-align: left; font-weight: 600; color: #667eea; border-bottom: 2px solid #e0e0e0; }\n");
        html.append("        .history-table td { padding: 15px; border-bottom: 1px solid #f0f0f0; }\n");
        html.append("        .history-table tr:hover { background: #f8f9fa; }\n");
        html.append("        .rank-badge { display: inline-block; padding: 5px 12px; border-radius: 12px; font-weight: bold; font-size: 0.9em; }\n");
        html.append("        .rank-1 { background: #FFD700; color: white; }\n");
        html.append("        .rank-2 { background: #C0C0C0; color: white; }\n");
        html.append("        .rank-3 { background: #CD7F32; color: white; }\n");
        html.append("        .rank-other { background: #e0e0e0; color: #666; }\n");
        html.append("        .no-data { text-align: center; padding: 40px; color: #999; font-size: 1.1em; }\n");
        html.append("        @media (max-width: 768px) {\n");
        html.append("            .profile-header { flex-direction: column; text-align: center; }\n");
        html.append("            .username { font-size: 2em; }\n");
        html.append("            .stats-grid { grid-template-columns: 1fr; }\n");
        html.append("            .history-table { font-size: 0.9em; }\n");
        html.append("            .history-table th, .history-table td { padding: 10px 5px; }\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <a href=\"/leaderboard\" class=\"back-button\">← Back to Leaderboard</a>\n");
        html.append("        \n");
        html.append("        <!-- Profile Header -->\n");
        html.append("        <div class=\"profile-header\">\n");

        // Avatar Section
        html.append("            <div class=\"avatar-large\">\n");
        String avatarUrl = player.avatarUrl;
        String initial = player.username.substring(0, 1).toUpperCase();

        // DEBUG
        System.out.println("🎨 [ProfileHandler] Avatar for " + player.username + ": " + avatarUrl);

        if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
            String avatarSrc = getAvatarSrc(avatarUrl);
            System.out.println("   → Using avatar: " + avatarSrc);

            // Tạo fallback với chữ cái
            html.append("                <span style=\"position: absolute; z-index: 1;\">")
                    .append(initial)
                    .append("</span>\n");

            // Thêm ảnh overlay
            html.append("                <img src=\"").append(escapeHtml(avatarSrc))
                    .append("\" alt=\"Avatar\" ")
                    .append("style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; border-radius: 50%; z-index: 2;\" ")
                    .append("onerror=\"console.error('Avatar load failed:', this.src); this.style.display='none';\">\n");
        } else {
            System.out.println("   → No avatar, using letter: " + initial);
            html.append("                <span>").append(initial).append("</span>\n");
        }
        html.append("            </div>\n");

        // Player Info Section
        html.append("            <div class=\"profile-info\">\n");
        html.append("                <div class=\"username\">");
        html.append(escapeHtml(player.username));
        if (player.totalGames == 0) {
            html.append("<span class=\"new-badge\">NEW PLAYER</span>");
        }
        html.append("</div>\n");

        html.append("                <div class=\"fullname\">");
        html.append(escapeHtml(player.fullName != null ? player.fullName : ""));
        html.append("</div>\n");

        html.append("                <div class=\"profile-meta\">\n");
        html.append("                    <div class=\"meta-item\">\n");
        html.append("                        <span>📧</span>\n");
        html.append("                        <span>").append(escapeHtml(player.email != null ? player.email : "N/A")).append("</span>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"meta-item\">\n");
        html.append("                        <span>🎂</span>\n");
        html.append("                        <span>Age: ").append(player.age > 0 ? player.age : "N/A").append("</span>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"meta-item\">\n");
        html.append("                        <span>📅</span>\n");
        html.append("                        <span>Joined: ");
        html.append(player.createdAt != null ? player.createdAt.toString().substring(0, 10) : "N/A");
        html.append("</span>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");

        // Stats Grid
        html.append("        <div class=\"stats-grid\">\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-icon\">🏆</div>\n");
        html.append("                <div class=\"stat-value\">").append(String.format("%,d", player.totalScore)).append("</div>\n");
        html.append("                <div class=\"stat-label\">Total Score</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-icon\">🎮</div>\n");
        html.append("                <div class=\"stat-value\">").append(player.totalGames).append("</div>\n");
        html.append("                <div class=\"stat-label\">Games Played</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-icon\">🥇</div>\n");
        html.append("                <div class=\"stat-value\">").append(player.wins).append("</div>\n");
        html.append("                <div class=\"stat-label\">Wins</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"stat-icon\">📈</div>\n");
        html.append("                <div class=\"stat-value\">").append(winRateStr).append("%</div>\n");
        html.append("                <div class=\"stat-label\">Win Rate</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");

        // Subject Scores Section
        html.append("        <div class=\"section\">\n");
        html.append("            <div class=\"section-title\">\n");
        html.append("                <span>📚</span>\n");
        html.append("                <span>Subject Scores</span>\n");
        html.append("            </div>\n");

        // Math
        html.append("            <div class=\"progress-bar-container\">\n");
        html.append("                <div class=\"progress-label\">\n");
        html.append("                    <span>🔢 Math</span>\n");
        html.append("                    <span>").append(String.format("%,d", player.mathScore)).append(" points</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"progress-bar\">\n");
        html.append("                    <div class=\"progress-fill\" style=\"width: ").append(calculatePercentage(player.mathScore, player.totalScore)).append("%\">\n");
        html.append("                        ").append(calculatePercentage(player.mathScore, player.totalScore)).append("%\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");

        // English
        html.append("            <div class=\"progress-bar-container\">\n");
        html.append("                <div class=\"progress-label\">\n");
        html.append("                    <span>🇬🇧 English</span>\n");
        html.append("                    <span>").append(String.format("%,d", player.englishScore)).append(" points</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"progress-bar\">\n");
        html.append("                    <div class=\"progress-fill\" style=\"width: ").append(calculatePercentage(player.englishScore, player.totalScore)).append("%\">\n");
        html.append("                        ").append(calculatePercentage(player.englishScore, player.totalScore)).append("%\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");

        // Literature
        html.append("            <div class=\"progress-bar-container\">\n");
        html.append("                <div class=\"progress-label\">\n");
        html.append("                    <span>📖 Literature</span>\n");
        html.append("                    <span>").append(String.format("%,d", player.literatureScore)).append(" points</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"progress-bar\">\n");
        html.append("                    <div class=\"progress-fill\" style=\"width: ").append(calculatePercentage(player.literatureScore, player.totalScore)).append("%\">\n");
        html.append("                        ").append(calculatePercentage(player.literatureScore, player.totalScore)).append("%\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");

        // Game History Section
        html.append("        <div class=\"section\">\n");
        html.append("            <div class=\"section-title\">\n");
        html.append("                <span>📊</span>\n");
        html.append("                <span>Game History (Last 10 Games)</span>\n");
        html.append("            </div>\n");

        if (history.isEmpty()) {
            html.append("            <div class=\"no-data\">\n");
            html.append("                😔 No game history yet. Start playing to see your stats!\n");
            html.append("            </div>\n");
        } else {
            html.append("            <table class=\"history-table\">\n");
            html.append("                <thead>\n");
            html.append("                    <tr>\n");
            html.append("                        <th>Date</th>\n");
            html.append("                        <th>Subject</th>\n");
            html.append("                        <th>Difficulty</th>\n");
            html.append("                        <th>Score</th>\n");
            html.append("                        <th>Correct</th>\n");
            html.append("                        <th>Rank</th>\n");
            html.append("                        <th>Time</th>\n");
            html.append("                    </tr>\n");
            html.append("                </thead>\n");
            html.append("                <tbody>\n");

            for (GameResultDAO.GameResult game : history) {
                String rankClass = game.rankPosition == 1 ? "rank-1" :
                        game.rankPosition == 2 ? "rank-2" :
                                game.rankPosition == 3 ? "rank-3" : "rank-other";

                html.append("                    <tr>\n");
                html.append("                        <td>").append(game.createdAt != null ? game.createdAt.toString().substring(0, 16) : "N/A").append("</td>\n");
                html.append("                        <td>").append(escapeHtml(game.subject != null ? game.subject : "N/A")).append("</td>\n");
                html.append("                        <td>").append(escapeHtml(game.difficulty != null ? game.difficulty : "N/A")).append("</td>\n");
                html.append("                        <td><strong>").append(game.score).append("</strong></td>\n");
                html.append("                        <td>").append(game.correctAnswers).append("/").append(game.correctAnswers + game.wrongAnswers).append("</td>\n");
                html.append("                        <td><span class=\"rank-badge ").append(rankClass).append("\">#").append(game.rankPosition).append("</span></td>\n");
                html.append("                        <td>").append(game.timeTaken).append("s</td>\n");
                html.append("                    </tr>\n");
            }

            html.append("                </tbody>\n");
            html.append("            </table>\n");
        }

        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    private int calculatePercentage(int part, int total) {
        if (total == 0) return 0;
        return (int) ((part * 100.0) / total);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private String getAvatarSrc(String avatarFileName) {
        if (avatarFileName == null || avatarFileName.isEmpty()) {
            return "/avatars/avatar4.png";
        }

        if (avatarFileName.startsWith("http://") || avatarFileName.startsWith("https://")) {
            return avatarFileName;
        }

        if (!avatarFileName.contains("/") && !avatarFileName.contains("\\")) {
            return "/avatars/" + avatarFileName;
        }

        return avatarFileName;
    }

    private void sendError(HttpExchange exchange, String message) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>Error</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; margin: 0; }\n");
        html.append("        .error-box { background: white; padding: 40px; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); text-align: center; }\n");
        html.append("        h1 { color: #e74c3c; margin-bottom: 20px; }\n");
        html.append("        p { color: #666; margin-bottom: 30px; }\n");
        html.append("        a { display: inline-block; background: #667eea; color: white; padding: 12px 30px; border-radius: 25px; text-decoration: none; transition: background 0.3s; }\n");
        html.append("        a:hover { background: #764ba2; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"error-box\">\n");
        html.append("        <h1>❌ Error</h1>\n");
        html.append("        <p>").append(escapeHtml(message)).append("</p>\n");
        html.append("        <a href=\"/leaderboard\">← Back to Leaderboard</a>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");

        WebServer.sendResponse(exchange, 400, html.toString(), "text/html");
    }
}