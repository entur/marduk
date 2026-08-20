package no.rutebanken.marduk.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every cron schedule in the helm values has to survive the {@code +}-to-space transform the ConfigMap
 * applies and then parse as a Spring cron expression.
 *
 * <p>Worth a test because of how it fails otherwise. {@code @Scheduled} parses its cron eagerly, so a value
 * Spring cannot read fails the application context at startup - every pod crash-loops, and the cause is a
 * one-character edit in a values file that looked harmless. Quartz accepted spellings Spring does not.
 *
 * <p>Verified: {@code ?} is accepted in both the day-of-month and day-of-week positions and behaves as
 * {@code *}, which is what lets the same helm value render both spellings.
 */
class CronScheduleTest {

    /** Matches {@code prevalidationCronSchedule: 0+30+23+?+*+*} and friends. */
    private static final Pattern CRON_VALUE = Pattern.compile("(\\w*[Cc]ron[A-Za-z]*):\\s*(\\S+)");

    private record Schedule(Path file, String key, String quartzValue) {
        String springValue() {
            return quartzValue.replace("+", " ");
        }
    }

    @Test
    void everyHelmCronScheduleParsesAsASpringCron() {
        List<Schedule> schedules = helmSchedules();

        assertTrue(schedules.size() >= 9,
                "Found only " + schedules.size() + " cron schedules; the pattern probably stopped matching, "
                        + "which would make this test pass without checking anything");

        for (Schedule schedule : schedules) {
            try {
                CronExpression.parse(schedule.springValue());
            } catch (IllegalArgumentException e) {
                fail("%s in %s is '%s', which becomes '%s' and Spring cannot parse it: %s"
                        .formatted(schedule.key(), schedule.file().getFileName(), schedule.quartzValue(),
                                schedule.springValue(), e.getMessage()));
            }
        }
    }

    @Test
    void theTransformPreservesTheScheduleRatherThanJustParsing() {
        // Parsing is not enough: a transform that silently changed the meaning would still parse. These are
        // the deployed values, with the times they are meant to fire.
        assertEquals("0 15 23 ? * MON-FRI", "0+15+23+?+*+MON-FRI".replace("+", " "));
        assertEquals(
                java.time.LocalDateTime.of(2026, 8, 17, 23, 15),
                CronExpression.parse("0 15 23 ? * MON-FRI")
                        .next(java.time.LocalDateTime.of(2026, 8, 17, 12, 0)));
        // The OSM fetch is hourly at 11 minutes past, not daily - '?' sits in the day-of-week slot here.
        assertEquals(
                java.time.LocalDateTime.of(2026, 8, 17, 12, 11),
                CronExpression.parse("0 11 * * * ?")
                        .next(java.time.LocalDateTime.of(2026, 8, 17, 12, 0)));
    }

    /** Matches the fallback inside {@code @Scheduled(cron = "${some.key:0 11 * * * ?}")}. */
    private static final Pattern SCHEDULED_DEFAULT =
            Pattern.compile("@Scheduled\\(\\s*cron\\s*=\\s*\"\\$\\{([^:}]+):([^}\"]+)}\"");

    @Test
    void everyScheduledCronDefaultParses() {
        // The helm check above cannot see these: a @Scheduled default lives in code and applies wherever the
        // ConfigMap does not set the key. Quartz accepted a bare step like "/5"; Spring rejects it, and the
        // context then fails at startup. That exact value was the old default for the candidate graph
        // monitor, which has no ConfigMap override at all.
        List<String[]> defaults = scheduledCronDefaults();

        assertTrue(!defaults.isEmpty(), "No @Scheduled cron defaults found; the pattern stopped matching");

        for (String[] keyAndDefault : defaults) {
            try {
                CronExpression.parse(keyAndDefault[1]);
            } catch (IllegalArgumentException e) {
                fail("The @Scheduled default for %s is '%s', which Spring cannot parse: %s"
                        .formatted(keyAndDefault[0], keyAndDefault[1], e.getMessage()));
            }
        }
    }

    private static List<String[]> scheduledCronDefaults() {
        List<String[]> defaults = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(path -> {
                try {
                    Matcher matcher = SCHEDULED_DEFAULT.matcher(Files.readString(path));
                    while (matcher.find()) {
                        defaults.add(new String[]{matcher.group(1), matcher.group(2)});
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return defaults;
    }

    private static List<Schedule> helmSchedules() {
        List<Schedule> schedules = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("helm"))) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .forEach(path -> collectFrom(path, schedules));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return schedules;
    }

    private static void collectFrom(Path path, List<Schedule> schedules) {
        try {
            Matcher matcher = CRON_VALUE.matcher(Files.readString(path));
            while (matcher.find()) {
                schedules.add(new Schedule(path, matcher.group(1), matcher.group(2)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
