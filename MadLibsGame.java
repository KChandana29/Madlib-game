package madlibgame;

import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MadLibsGame {
    public static void main(String[] args) throws Exception {
        // Load the MySQL JDBC driver
        Class.forName("com.mysql.cj.jdbc.Driver");
        System.out.println("MySQL JDBC driver loaded.");

        // Database connection information
        final String DB_URL = "jdbc:mysql://localhost:3306/mad_libs";
        final String DB_USER = "";//give user db username
        final String DB_PASSWORD = "";//give your db password

        // Stories for the Mad Libs game
        final Map<String, List<String>> STORY_CATEGORIES = new HashMap<>();
        List<String> adventureStories = Arrays.asList(
            "Once upon a time, there was a [adjective] [noun] who loved to [verb].",
            "In a [adjective] land, there lived a [noun] who wanted to be a [profession]."
        );
        List<String> comedyStories = Arrays.asList(
            "A [adjective] [noun] walked into a [place] and started [verb].",
            "Why did the [noun] cross the road? To [verb] the other side!"
        );
        List<String> mysteryStories = Arrays.asList(
            "The detective entered the [place] to investigate the [adjective] crime.",
            "The missing [noun] was found in the [adjective] room."
        );
        List<String> joyStories = Arrays.asList(
            "It was a [adjective] day, and everyone was [verb] with joy.",
            "The [noun] laughed and [verb] with delight."
        );

        STORY_CATEGORIES.put("Adventure", adventureStories);
        STORY_CATEGORIES.put("Comedy", comedyStories);
        STORY_CATEGORIES.put("Mystery", mysteryStories);
        STORY_CATEGORIES.put("Joy", joyStories);

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("************************************************************************");
            System.out.println("Welcome to Mad Libs Game!");
            System.out.println("************************************************************************");
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();

            int userId = createUser(connection, username);

            while (true) {

                System.out.println("************************************************************************");
                System.out.println("Choose a category:");
                System.out.println("-------------------------");
                int categoryIndex = 1;
                for (String category : STORY_CATEGORIES.keySet()) {
                    System.out.println(categoryIndex + ". " + category);
                    categoryIndex++;
                }
                System.out.println("-------------------------");
                System.out.print("Enter the category number (0 to quit): ");
                int categoryNumber = scanner.nextInt();
                scanner.nextLine(); // Consume the newline character

                if (categoryNumber == 0) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (categoryNumber < 1 || categoryNumber > STORY_CATEGORIES.size()) {
                    System.out.println("Invalid category number. Try again.");
                    continue;
                }

                String selectedCategory = (String) STORY_CATEGORIES.keySet().toArray()[categoryNumber - 1];
                List<String> stories = STORY_CATEGORIES.get(selectedCategory);
                Map<String, String> answers = getAnswersForCategory(selectedCategory);
                System.out.println("-------------------------");
                System.out.println("Choose a story:");
                int storyIndex = 1;
                for (String story : stories) {
                    System.out.println(storyIndex + ". " + story);
                    storyIndex++;
                }
                System.out.print("Enter the story number (0 to go back): ");
                int storyNumber = scanner.nextInt();
                scanner.nextLine(); // Consume the newline character

                if (storyNumber == 0) {
                    continue;
                }

                if (storyNumber < 1 || storyNumber > stories.size()) {
                    System.out.println("Invalid story number. Try again.");
                    continue;
                }
                long startTime = System.currentTimeMillis();
                String selectedStory = stories.get(storyNumber - 1);
                String[] placeholders = extractPlaceholders(selectedStory);
                Map<String, String> userInputs = new HashMap<>();
                System.out.println("-------------------------");
                for (String placeholder : placeholders) {
                    System.out.print("Enter a " + placeholder + ": ");
                    String input = scanner.nextLine();
                    userInputs.put(placeholder, input);
                }

                String filledStory = fillStory(selectedStory, userInputs);
                System.out.println("-------------------------");
                System.out.println("Your filled story:\n" + filledStory);

                // Calculate and update user score and time taken
                int score = calculateScore(selectedStory, userInputs, answers);
                long endTime = System.currentTimeMillis();
                long timeTakenMillis = endTime - startTime;
                long timeTakenSeconds = timeTakenMillis / 1000;
                updateUserScore(connection, userId, score, timeTakenSeconds);

                // Show the correct answers
                System.out.println("-------------------------");
                System.out.println("\nCorrect answers:");
                for (String placeholder : answers.keySet()) {
                    String correctAnswer = answers.get(placeholder);
                    System.out.println(placeholder + ": " + correctAnswer);
                }

                // Show the scorecard
                showScorecard(connection);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static int createUser(Connection connection, String username) throws SQLException {
        String query = "INSERT INTO users (username) VALUES (?) ON DUPLICATE KEY UPDATE iduser=LAST_INSERT_ID(iduser)";
        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.executeUpdate();

            ResultSet resultSet = statement.getGeneratedKeys();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            } else {
                throw new SQLException("Failed to create or fetch user ID.");
            }
        }
    }

    private static Map<String, String> getAnswersForCategory(String category) {
        Map<String, String> answers = new HashMap<>();
        if (category.equals("Adventure")) {
            answers.put("adjective", "brave");
            answers.put("noun", "hero");
            answers.put("verb", "explore");
        } else if (category.equals("Comedy")) {
            answers.put("adjective", "funny");
            answers.put("noun", "clown");
            answers.put("verb", "joke");
            answers.put("place", "circus");
        } else if (category.equals("Mystery")) {
            answers.put("adjective", "clever");
            answers.put("noun", "detective");
            answers.put("verb", "investigate");
            answers.put("place", "mansion");
        } else if (category.equals("Joy")) {
            answers.put("adjective", "happy");
            answers.put("noun", "children");
            answers.put("verb", "play");
        }
        return answers;
    }

    private static String[] extractPlaceholders(String story) {
        List<String> placeholders = new ArrayList<>();
        int startIndex = 0;
        while (startIndex != -1) {
            startIndex = story.indexOf('[', startIndex);
            int endIndex = story.indexOf(']', startIndex);
            if (startIndex != -1 && endIndex != -1) {
                String placeholder = story.substring(startIndex + 1, endIndex);
                placeholders.add(placeholder);
                startIndex = endIndex;
            }
        }
        return placeholders.toArray(new String[0]);
    }

    private static String fillStory(String story, Map<String, String> userInputs) {
        for (String placeholder : userInputs.keySet()) {
            String value = userInputs.get(placeholder);
            story = story.replace("[" + placeholder + "]", value);
        }
        return story;
    }

    private static int calculateScore(String story, Map<String, String> userInputs, Map<String, String> answers) {
        int score = 0;
        for (String placeholder : userInputs.keySet()) {
            String userValue = userInputs.get(placeholder);
            String correctValue = answers.get(placeholder);
            if (userValue != null && userValue.equalsIgnoreCase(correctValue)) {
                score++;
            }
        }
        return score;
    }

    private static void updateUserScore(Connection connection, int userId, int score, long startTime) throws SQLException {
        long endTime = System.currentTimeMillis();
        long timeTakenSeconds = (endTime - startTime) / 1000;
        String query = "UPDATE users SET score = score + ?, time_taken = ? WHERE iduser = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, score);
            statement.setLong(2, timeTakenSeconds);
            statement.setInt(3, userId);
            statement.executeUpdate();
        }
    }

    private static void showScorecard(Connection connection) throws SQLException {
        String query = "SELECT username, score, time_taken FROM users ORDER BY score DESC";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            System.out.println("-------------------------");
            System.out.println("Scorecard:");
            System.out.println("-------------------------");
            System.out.println("Username\tScore\tTime (s)");
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                int score = resultSet.getInt("score");
                long timeTakenSeconds = resultSet.getLong("time_taken");
                System.out.println(username + "\t\t" + score + "\t\t" + timeTakenSeconds);
            }
        }
    }
}
