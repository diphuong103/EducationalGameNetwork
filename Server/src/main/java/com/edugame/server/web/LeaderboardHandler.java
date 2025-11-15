package com.edugame.server.web;

import com.edugame.server.database.UserDAO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * LeaderboardHandler - Display ALL players with search functionality
 * Uses UserDAO to show ALL registered users
 */
public class LeaderboardHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Get ALL players from UserDAO
            UserDAO userDAO = new UserDAO();
            List<UserDAO.PlayerInfo> allPlayers = userDAO.getAllPlayersForLeaderboard();

            // Build HTML
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html lang=\"vi\">\n");
            html.append("<head>\n");
            html.append("    <meta charset=\"UTF-8\">\n");
            html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("    <title>🏆 Leaderboard - Educational Game</title>\n");
            html.append("    <style>\n");
            html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
            html.append("        body {\n");
            html.append("            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n");
            html.append("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
            html.append("            min-height: 100vh;\n");
            html.append("            padding: 40px 20px;\n");
            html.append("        }\n");
            html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
            html.append("        .header { text-align: center; color: white; margin-bottom: 30px; }\n");
            html.append("        h1 { font-size: 3em; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }\n");
            html.append("        .subtitle { font-size: 1.2em; opacity: 0.9; }\n");
            html.append("        .top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; gap: 20px; }\n");
            html.append("        .back-button { display: inline-block; background: rgba(255,255,255,0.2); color: white; padding: 10px 20px; border-radius: 25px; text-decoration: none; transition: background 0.3s; white-space: nowrap; }\n");
            html.append("        .back-button:hover { background: rgba(255,255,255,0.3); }\n");
            html.append("        .search-container { flex: 1; max-width: 400px; position: relative; }\n");
            html.append("        .search-box { width: 100%; padding: 12px 45px 12px 20px; border: none; border-radius: 25px; font-size: 1em; background: white; box-shadow: 0 4px 15px rgba(0,0,0,0.2); transition: box-shadow 0.3s; }\n");
            html.append("        .search-box:focus { outline: none; box-shadow: 0 6px 20px rgba(0,0,0,0.3); }\n");
            html.append("        .search-icon { position: absolute; right: 18px; top: 50%; transform: translateY(-50%); font-size: 1.2em; color: #667eea; }\n");
            html.append("        .stats-bar { background: rgba(255,255,255,0.2); padding: 15px 25px; border-radius: 15px; color: white; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 20px; }\n");
            html.append("        .stat-item { display: flex; align-items: center; gap: 10px; }\n");
            html.append("        .stat-value { font-size: 1.5em; font-weight: bold; }\n");
            html.append("        .leaderboard { background: white; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n");
            html.append("        .leaderboard-header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; display: grid; grid-template-columns: 80px 1fr 120px 120px 120px; gap: 15px; font-weight: bold; font-size: 0.9em; position: sticky; top: 0; z-index: 10; }\n");
            html.append("        .player-row { display: grid; grid-template-columns: 80px 1fr 120px 120px 120px; gap: 15px; padding: 20px; border-bottom: 1px solid #f0f0f0; align-items: center; transition: background 0.2s, transform 0.2s; }\n");
            html.append("        .player-row:hover { background: #f8f9fa; transform: translateX(5px); }\n");
            html.append("        .player-row:last-child { border-bottom: none; }\n");
            html.append("        .player-row.hidden { display: none; }\n");
            html.append("        .player-row.no-games { opacity: 0.6; }\n");
            html.append("        .rank { text-align: center; font-weight: bold; font-size: 1.5em; }\n");
            html.append("        .rank-1 { color: #FFD700; text-shadow: 1px 1px 2px rgba(255,215,0,0.3); }\n");
            html.append("        .rank-2 { color: #C0C0C0; }\n");
            html.append("        .rank-3 { color: #CD7F32; }\n");
            html.append("        .player-info { display: flex; align-items: center; gap: 15px; }\n");
            html.append("        .avatar { width: 50px; height: 50px; border-radius: 50%; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 1.2em; flex-shrink: 0; }\n");
            html.append("        .player-name { display: flex; flex-direction: column; }\n");
            html.append("        .username-link { font-weight: bold; color: #333; font-size: 1.1em; text-decoration: none; transition: color 0.3s; cursor: pointer; }\n");
            html.append("        .username-link:hover { color: #667eea; text-decoration: underline; }\n");
            html.append("        .fullname { color: #666; font-size: 0.9em; }\n");
            html.append("        .new-badge { display: inline-block; background: #4ade80; color: white; padding: 2px 8px; border-radius: 10px; font-size: 0.75em; margin-left: 5px; }\n");
            html.append("        .stat { text-align: center; }\n");
            html.append("        .stat-value-cell { font-size: 1.3em; font-weight: bold; color: #667eea; }\n");
            html.append("        .stat-label { font-size: 0.8em; color: #999; margin-top: 5px; }\n");
            html.append("        .trophy { font-size: 2em; }\n");
            html.append("        .no-data { text-align: center; padding: 60px; color: #999; font-size: 1.2em; }\n");
            html.append("        .no-results { text-align: center; padding: 60px; color: #999; font-size: 1.2em; display: none; }\n");
            html.append("        .no-results.show { display: block; }\n");
            html.append("        .refresh-info { text-align: center; color: white; margin-top: 20px; opacity: 0.8; }\n");
            html.append("        .highlight { background: #fff3cd; }\n");
            html.append("        @media (max-width: 768px) {\n");
            html.append("            .top-bar { flex-direction: column; }\n");
            html.append("            .search-container { max-width: 100%; }\n");
            html.append("            .stats-bar { justify-content: center; }\n");
            html.append("            .leaderboard-header, .player-row { grid-template-columns: 60px 1fr 80px; }\n");
            html.append("            .stat:nth-child(4), .stat:nth-child(5), .leaderboard-header > *:nth-child(4), .leaderboard-header > *:nth-child(5) { display: none; }\n");
            html.append("        }\n");
            html.append("    </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("    <div class=\"container\">\n");
            html.append("        <div class=\"header\">\n");
            html.append("            <h1>🏆 Leaderboard</h1>\n");
            html.append("            <p class=\"subtitle\">All Players Ranking</p>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"top-bar\">\n");
            html.append("            <a href=\"/\" class=\"back-button\">← Back to Home</a>\n");
            html.append("            <div class=\"search-container\">\n");
            html.append("                <input type=\"text\" id=\"searchBox\" class=\"search-box\" placeholder=\"Search player name...\" autocomplete=\"off\">\n");
            html.append("                <span class=\"search-icon\">🔍</span>\n");
            html.append("            </div>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"stats-bar\">\n");
            html.append("            <div class=\"stat-item\">\n");
            html.append("                <span>👥 Total Players:</span>\n");
            html.append("                <span class=\"stat-value\" id=\"totalPlayers\">").append(allPlayers.size()).append("</span>\n");
            html.append("            </div>\n");
            html.append("            <div class=\"stat-item\">\n");
            html.append("                <span>👁️ Showing:</span>\n");
            html.append("                <span class=\"stat-value\" id=\"showingCount\">").append(allPlayers.size()).append("</span>\n");
            html.append("            </div>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"leaderboard\">\n");
            html.append("            <div class=\"leaderboard-header\">\n");
            html.append("                <div>Rank</div>\n");
            html.append("                <div>Player</div>\n");
            html.append("                <div>Total Score</div>\n");
            html.append("                <div>Games</div>\n");
            html.append("                <div>Wins</div>\n");
            html.append("            </div>\n");
            html.append("            <div id=\"playerList\">\n");

            if (allPlayers.isEmpty()) {
                html.append("                <div class=\"no-data\">\n");
                html.append("                    😔 No players yet. Be the first to register!\n");
                html.append("                </div>\n");
            } else {
                for (int i = 0; i < allPlayers.size(); i++) {
                    UserDAO.PlayerInfo player = allPlayers.get(i);
                    int rank = i + 1;
                    String rankClass = "";
                    String trophy = "";

                    if (rank == 1 && player.totalGames > 0) {
                        rankClass = "rank-1";
                        trophy = "🥇";
                    } else if (rank == 2 && player.totalGames > 0) {
                        rankClass = "rank-2";
                        trophy = "🥈";
                    } else if (rank == 3 && player.totalGames > 0) {
                        rankClass = "rank-3";
                        trophy = "🥉";
                    }

                    String initial = player.username.substring(0, 1).toUpperCase();
                    String fullName = player.fullName != null ? player.fullName : "";

                    // Check if new player (no games played)
                    String noGamesClass = player.totalGames == 0 ? " no-games" : "";

                    html.append("                <div class=\"player-row").append(noGamesClass).append("\" data-username=\"")
                            .append(escapeHtml(player.username.toLowerCase()))
                            .append("\" data-fullname=\"")
                            .append(escapeHtml(fullName.toLowerCase()))
                            .append("\">\n");
                    html.append("                    <div class=\"rank ").append(rankClass).append("\">\n");

                    if (!trophy.isEmpty()) {
                        html.append("                        <div class=\"trophy\">").append(trophy).append("</div>\n");
                    }

                    html.append("                        <div>").append(rank).append("</div>\n");
                    html.append("                    </div>\n");
                    html.append("                    <div class=\"player-info\">\n");

                    // Hiển thị avatar với fallback
                    String avatarUrl = player.avatarUrl;
                    if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
                        String avatarSrc = getAvatarSrc(avatarUrl);
                        html.append("                        <div class=\"avatar\" style=\"overflow: hidden; position: relative;\">\n");
                        html.append("                            <div style=\"width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;\">")
                                .append(initial).append("</div>\n");
                        html.append("                            <img src=\"").append(escapeHtml(avatarSrc))
                                .append("\" alt=\"Avatar\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover;\" onerror=\"this.style.display='none';\">\n");
                        html.append("                        </div>\n");
                    } else {
                        html.append("                        <div class=\"avatar\">").append(initial).append("</div>\n");
                    }

                    html.append("                        <div class=\"player-name\">\n");
                    html.append("                            <a href=\"/profile?id=").append(player.userId).append("\" class=\"username-link\">")
                            .append(escapeHtml(player.username));

                    // Add "NEW" badge for players with no games
                    if (player.totalGames == 0) {
                        html.append("<span class=\"new-badge\">NEW</span>");
                    }

                    html.append("</a>\n");
                    html.append("                            <div class=\"fullname\">").append(escapeHtml(fullName)).append("</div>\n");
                    html.append("                        </div>\n");
                    html.append("                    </div>\n");
                    html.append("                    <div class=\"stat\">\n");
                    html.append("                        <div class=\"stat-value-cell\">").append(String.format("%,d", player.totalScore)).append("</div>\n");
                    html.append("                        <div class=\"stat-label\">points</div>\n");
                    html.append("                    </div>\n");
                    html.append("                    <div class=\"stat\">\n");
                    html.append("                        <div class=\"stat-value-cell\">").append(player.totalGames).append("</div>\n");
                    html.append("                        <div class=\"stat-label\">games</div>\n");
                    html.append("                    </div>\n");
                    html.append("                    <div class=\"stat\">\n");
                    html.append("                        <div class=\"stat-value-cell\">").append(player.wins).append("</div>\n");
                    html.append("                        <div class=\"stat-label\">wins</div>\n");
                    html.append("                    </div>\n");
                    html.append("                </div>\n");
                }
            }

            html.append("            </div>\n");
            html.append("            <div class=\"no-results\" id=\"noResults\">\n");
            html.append("                🔍 No players found matching your search\n");
            html.append("            </div>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"refresh-info\">\n");
            html.append("            📊 Showing all players | Last updated: ");
            html.append(java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            html.append("        </div>\n");
            html.append("    </div>\n");
            html.append("    <script>\n");
            html.append("        const searchBox = document.getElementById('searchBox');\n");
            html.append("        const playerRows = document.querySelectorAll('.player-row');\n");
            html.append("        const showingCount = document.getElementById('showingCount');\n");
            html.append("        const noResults = document.getElementById('noResults');\n");
            html.append("        const totalPlayers = ").append(allPlayers.size()).append(";\n");
            html.append("        searchBox.addEventListener('input', function() {\n");
            html.append("            const searchTerm = this.value.toLowerCase().trim();\n");
            html.append("            let visibleCount = 0;\n");
            html.append("            playerRows.forEach(row => {\n");
            html.append("                const username = row.getAttribute('data-username');\n");
            html.append("                const fullname = row.getAttribute('data-fullname');\n");
            html.append("                if (username.includes(searchTerm) || fullname.includes(searchTerm)) {\n");
            html.append("                    row.classList.remove('hidden');\n");
            html.append("                    visibleCount++;\n");
            html.append("                    if (searchTerm.length > 0) {\n");
            html.append("                        row.classList.add('highlight');\n");
            html.append("                    } else {\n");
            html.append("                        row.classList.remove('highlight');\n");
            html.append("                    }\n");
            html.append("                } else {\n");
            html.append("                    row.classList.add('hidden');\n");
            html.append("                    row.classList.remove('highlight');\n");
            html.append("                }\n");
            html.append("            });\n");
            html.append("            showingCount.textContent = visibleCount;\n");
            html.append("            if (visibleCount === 0 && searchTerm.length > 0) {\n");
            html.append("                noResults.classList.add('show');\n");
            html.append("            } else {\n");
            html.append("                noResults.classList.remove('show');\n");
            html.append("            }\n");
            html.append("        });\n");
            html.append("        document.addEventListener('keydown', function(e) {\n");
            html.append("            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {\n");
            html.append("                e.preventDefault();\n");
            html.append("                searchBox.focus();\n");
            html.append("            }\n");
            html.append("        });\n");
            html.append("        searchBox.addEventListener('keydown', function(e) {\n");
            html.append("            if (e.key === 'Escape') {\n");
            html.append("                this.value = '';\n");
            html.append("                this.dispatchEvent(new Event('input'));\n");
            html.append("                this.blur();\n");
            html.append("            }\n");
            html.append("        });\n");
            html.append("    </script>\n");
            html.append("</body>\n");
            html.append("</html>");

            WebServer.sendResponse(exchange, 200, html.toString(), "text/html");

        } catch (SQLException e) {
            e.printStackTrace();
            String error = "<html><body><h1>Error loading leaderboard</h1><p>" +
                    e.getMessage() + "</p></body></html>";
            WebServer.sendResponse(exchange, 500, error, "text/html");
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * ✅ XỬ LÝ AVATAR URL - THÊM VÀO LeaderboardHandler
     */
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
}