package com.riseup.verification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class TriviaTask implements VerificationTask {
    public record TriviaItem(String question, List<String> choices, int correct) {}

    public static final List<TriviaItem> TRIVIA_BANK =
            List.of(
                    new TriviaItem("What is the largest planet in our solar system?", List.of("Mars", "Jupiter", "Saturn", "Venus"), 1),
                    new TriviaItem("How many continents are there?", List.of("5", "6", "7", "8"), 2),
                    new TriviaItem("What gas do plants absorb from the air?", List.of("Oxygen", "Nitrogen", "Carbon dioxide", "Helium"), 2),
                    new TriviaItem("What is the capital of Canada?", List.of("Toronto", "Ottawa", "Vancouver", "Montreal"), 1),
                    new TriviaItem("How many minutes are in a day?", List.of("1440", "1240", "1640", "960"), 0),
                    new TriviaItem("Which language runs RiseUp's backend?", List.of("Java", "Go", "Python", "Ruby"), 2),
                    new TriviaItem("What is H2O commonly called?", List.of("Salt", "Water", "Hydrogen", "Ozone"), 1),
                    new TriviaItem("Which is the smallest prime number?", List.of("0", "1", "2", "3"), 2),
                    new TriviaItem("How many sides does a hexagon have?", List.of("5", "6", "7", "8"), 1),
                    new TriviaItem("Which ocean is the largest?", List.of("Atlantic", "Indian", "Arctic", "Pacific"), 3),
                    new TriviaItem("What is 15% of 200?", List.of("20", "25", "30", "35"), 2),
                    new TriviaItem("Who wrote 'Romeo and Juliet'?", List.of("Dickens", "Shakespeare", "Austen", "Tolkien"), 1));

    private final int index;

    public TriviaTask(int index) {
        this.index = index % TRIVIA_BANK.size();
    }

    public static TriviaTask generate(Random rng) {
        return new TriviaTask(rng.nextInt(TRIVIA_BANK.size()));
    }

    public TriviaItem question() {
        return TRIVIA_BANK.get(index);
    }

    @Override
    public String getTaskType() {
        return "trivia";
    }

    @Override
    public Map<String, Object> getTaskPrompt() {
        TriviaItem q = question();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", getTaskType());
        m.put("question", q.question());
        m.put("choices", q.choices());
        m.put("input", "choice");
        return m;
    }

    @Override
    public VerificationResult validate(VerificationResponse response) {
        TriviaItem q = question();
        String answer = response.getAnswer() == null ? "" : response.getAnswer().strip();
        if (answer.chars().allMatch(Character::isDigit) && !answer.isEmpty()
                && Integer.parseInt(answer) == q.correct()) {
            return new VerificationResult(true, "Correct!");
        }
        if (answer.toLowerCase(Locale.ROOT).equals(q.choices().get(q.correct()).toLowerCase(Locale.ROOT))) {
            return new VerificationResult(true, "Correct!");
        }
        return new VerificationResult(false, "Task not valid. Please try again.");
    }

    @Override
    public Map<String, Object> toState() {
        return Map.of("type", getTaskType(), "index", index);
    }

    @Override
    public boolean resetsOnFailure() {
        return true;
    }

    public int getIndex() {
        return index;
    }
}
