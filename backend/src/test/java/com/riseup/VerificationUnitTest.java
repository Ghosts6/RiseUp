package com.riseup;

import com.riseup.error.ValidationFailedException;
import com.riseup.verification.BasicImageDetector;
import com.riseup.verification.MathTask;
import com.riseup.verification.TriviaTask;
import com.riseup.verification.VerificationResponse;
import com.riseup.verification.VerificationTaskFactory;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationUnitTest {

    @Test
    void mathTaskValidatesAnswer() {
        MathTask task = new MathTask(2, 5, 1, "+");
        VerificationResponse ok = new VerificationResponse();
        ok.setAnswer(String.valueOf(task.answer()));
        ok.setCompleted(true);
        assertThat(task.validate(ok).isSuccess()).isTrue();

        VerificationResponse bad = new VerificationResponse();
        bad.setAnswer("0");
        bad.setCompleted(true);
        assertThat(task.validate(bad).isSuccess()).isFalse();
    }

    @Test
    void factoryCreatesStrategies() {
        VerificationTaskFactory factory = new VerificationTaskFactory(true, new BasicImageDetector(), new Random(1));
        assertThat(factory.create("math", null, 0)).isInstanceOf(MathTask.class);
        assertThat(factory.create("trivia", null, 0)).isInstanceOf(TriviaTask.class);
        assertThat(factory.create("object_detection", null, 2)).isInstanceOf(MathTask.class);
        assertThatThrownBy(() -> factory.create("sudoku", null, 0)).isInstanceOf(ValidationFailedException.class);
    }
}
