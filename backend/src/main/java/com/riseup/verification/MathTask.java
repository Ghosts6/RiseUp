package com.riseup.verification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class MathTask implements VerificationTask {
    private final int a, b, c;
    private final String op;

    public MathTask(int a, int b, int c, String op) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.op = op;
    }

    public static MathTask generate(Random rng) {
        return generate(rng, "medium");
    }

    public static MathTask generate(Random rng, String difficulty) {
        String diff = difficulty == null ? "medium" : difficulty.toLowerCase();
        return switch (diff) {
            case "easy" -> {
                // Addition / subtraction with small numbers
                boolean add = rng.nextBoolean();
                int x = rng.nextInt(20) + 2;
                int y = rng.nextInt(20) + 2;
                if (add) {
                    yield new MathTask(x, y, rng.nextInt(5), "+");
                }
                int hi = Math.max(x, y);
                int lo = Math.min(x, y);
                yield new MathTask(hi, lo, rng.nextInt(5), "-");
            }
            case "hard" -> {
                String[] ops = {"+", "-", "*"};
                String op = ops[rng.nextInt(ops.length)];
                if ("*".equals(op)) {
                    yield new MathTask(rng.nextInt(14) + 6, rng.nextInt(12) + 4, rng.nextInt(40) + 5, op);
                }
                yield new MathTask(rng.nextInt(180) + 40, rng.nextInt(160) + 20, rng.nextInt(50) + 5, op);
            }
            default -> {
                String[] ops = {"+", "-", "*"};
                String op = ops[rng.nextInt(ops.length)];
                if ("*".equals(op)) {
                    yield new MathTask(rng.nextInt(10) + 3, rng.nextInt(10) + 3, rng.nextInt(20) + 1, op);
                }
                yield new MathTask(rng.nextInt(88) + 12, rng.nextInt(88) + 12, rng.nextInt(20) + 1, op);
            }
        };
    }

    public String equation() {
        return "(" + a + " " + op + " " + b + ") + " + c;
    }

    public int answer() {
        int base =
                switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    default -> throw new IllegalStateException(op);
                };
        return base + c;
    }

    @Override
    public String getTaskType() {
        return "math";
    }

    @Override
    public Map<String, Object> getTaskPrompt() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", getTaskType());
        m.put("question", "Solve: " + equation());
        m.put("input", "number");
        return m;
    }

    @Override
    public VerificationResult validate(VerificationResponse response) {
        try {
            int given = Integer.parseInt(String.valueOf(response.getAnswer()).strip());
            if (given == answer()) return new VerificationResult(true, "Correct!");
            return new VerificationResult(false, "Not quite — try again.");
        } catch (Exception e) {
            return new VerificationResult(false, "Enter a whole number.");
        }
    }

    @Override
    public Map<String, Object> toState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", getTaskType());
        m.put("a", a);
        m.put("b", b);
        m.put("c", c);
        m.put("op", op);
        return m;
    }

    @Override
    public boolean resetsOnFailure() {
        return true;
    }
}
