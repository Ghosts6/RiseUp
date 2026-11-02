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
        String[] ops = {"+", "-", "*"};
        String op = ops[rng.nextInt(ops.length)];
        if ("*".equals(op)) {
            return new MathTask(rng.nextInt(10) + 3, rng.nextInt(10) + 3, rng.nextInt(20) + 1, op);
        }
        return new MathTask(rng.nextInt(88) + 12, rng.nextInt(88) + 12, rng.nextInt(20) + 1, op);
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
            return new VerificationResult(false, "Task not valid. Please try again.");
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
