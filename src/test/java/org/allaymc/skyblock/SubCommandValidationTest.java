package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 10: Sub-command argument validation

import net.jqwik.api.*;
import org.allaymc.skyblock.command.IsCommand;

/**
 * Property-based tests for Property 10: Sub-command argument validation.
 *
 * <p>For any {@code /is} sub-command that requires arguments (invite, kick, sethome),
 * executing it with missing or malformed arguments must result in a usage-hint message
 * being sent to the player and no state change occurring.</p>
 *
 * <p>The validation logic is tested via the package-private static helper methods on
 * {@link IsCommand} ({@code isInviteArgMissing}, {@code isKickArgMissing},
 * {@code inviteUsageHint}, {@code kickUsageHint}, {@code sethomeNotInOwnIslandError})
 * which are extracted from the private sub-command handlers. This avoids the need to
 * instantiate {@link IsCommand} (which requires a live AllayMC server) while still
 * verifying the core validation contract.</p>
 *
 * <p><b>Validates: Requirements 11.3</b></p>
 */
class SubCommandValidationTest {

    // =========================================================================
    // Property 10a: invite argument validation
    // Validates: Requirements 11.3
    // =========================================================================

    /**
     * For any null or empty player name argument to {@code /is invite}, the argument
     * must be detected as missing/malformed, and the usage-hint message must be non-empty
     * and contain the expected usage syntax.
     *
     * <p>This verifies that the validation guard in {@code executeInvite} correctly
     * identifies null and empty strings as invalid arguments, and that the usage hint
     * it would send is well-formed.</p>
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_inviteWithNullOrEmptyArgIsDetectedAsMissing(
            @ForAll("nullOrEmptyString") String badTargetName) {

        // The validation predicate must return true for null/empty args
        boolean isMissing = IsCommand.isInviteArgMissing(badTargetName);
        if (!isMissing) {
            throw new AssertionError(
                    "Property 10 violated: isInviteArgMissing() returned false for arg='"
                    + badTargetName + "' — null and empty strings must be detected as missing.");
        }

        // The usage hint that would be sent must contain the expected syntax
        String hint = IsCommand.inviteUsageHint();
        if (hint == null || hint.isEmpty()) {
            throw new AssertionError(
                    "Property 10 violated: inviteUsageHint() returned null or empty string.");
        }
        if (!hint.contains("/is invite")) {
            throw new AssertionError(
                    "Property 10 violated: inviteUsageHint() does not contain '/is invite'. Got: " + hint);
        }
    }

    /**
     * For any non-null, non-empty player name argument to {@code /is invite}, the
     * argument must NOT be detected as missing/malformed.
     *
     * <p>This is the complementary property: valid arguments must pass the guard.</p>
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_inviteWithNonEmptyArgIsNotDetectedAsMissing(
            @ForAll("nonEmptyString") String validTargetName) {

        boolean isMissing = IsCommand.isInviteArgMissing(validTargetName);
        if (isMissing) {
            throw new AssertionError(
                    "Property 10 violated: isInviteArgMissing() returned true for non-empty arg='"
                    + validTargetName + "' — non-empty strings must not be detected as missing.");
        }
    }

    // =========================================================================
    // Property 10b: kick argument validation
    // Validates: Requirements 11.3
    // =========================================================================

    /**
     * For any null or empty member name argument to {@code /is kick}, the argument
     * must be detected as missing/malformed, and the usage-hint message must be non-empty
     * and contain the expected usage syntax.
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_kickWithNullOrEmptyArgIsDetectedAsMissing(
            @ForAll("nullOrEmptyString") String badMemberName) {

        // The validation predicate must return true for null/empty args
        boolean isMissing = IsCommand.isKickArgMissing(badMemberName);
        if (!isMissing) {
            throw new AssertionError(
                    "Property 10 violated: isKickArgMissing() returned false for arg='"
                    + badMemberName + "' — null and empty strings must be detected as missing.");
        }

        // The usage hint that would be sent must contain the expected syntax
        String hint = IsCommand.kickUsageHint();
        if (hint == null || hint.isEmpty()) {
            throw new AssertionError(
                    "Property 10 violated: kickUsageHint() returned null or empty string.");
        }
        if (!hint.contains("/is kick")) {
            throw new AssertionError(
                    "Property 10 violated: kickUsageHint() does not contain '/is kick'. Got: " + hint);
        }
    }

    /**
     * For any non-null, non-empty member name argument to {@code /is kick}, the
     * argument must NOT be detected as missing/malformed.
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_kickWithNonEmptyArgIsNotDetectedAsMissing(
            @ForAll("nonEmptyString") String validMemberName) {

        boolean isMissing = IsCommand.isKickArgMissing(validMemberName);
        if (isMissing) {
            throw new AssertionError(
                    "Property 10 violated: isKickArgMissing() returned true for non-empty arg='"
                    + validMemberName + "' — non-empty strings must not be detected as missing.");
        }
    }

    // =========================================================================
    // Property 10c: sethome error message is well-formed
    // Validates: Requirements 11.3, 3.4
    // =========================================================================

    /**
     * The error message sent when a player executes {@code /is sethome} outside their
     * own island must be non-empty and convey that the player must be inside their island.
     *
     * <p>This verifies that the error message contract is satisfied: the message must
     * exist and be meaningful so the player understands why the command failed.</p>
     *
     * <p><b>Validates: Requirements 11.3, 3.4</b></p>
     */
    @Property(tries = 1)
    void property10_sethomeErrorMessageIsWellFormed() {
        String errorMsg = IsCommand.sethomeNotInOwnIslandError();

        if (errorMsg == null || errorMsg.isEmpty()) {
            throw new AssertionError(
                    "Property 10 violated: sethomeNotInOwnIslandError() returned null or empty string.");
        }

        // The message must mention "island" so the player understands the context
        if (!errorMsg.toLowerCase().contains("island")) {
            throw new AssertionError(
                    "Property 10 violated: sethomeNotInOwnIslandError() does not mention 'island'. Got: "
                    + errorMsg);
        }
    }

    // =========================================================================
    // Property 10d: biconditional — missing iff null or empty
    // Validates: Requirements 11.3
    // =========================================================================

    /**
     * Biconditional property: for any string, {@code isInviteArgMissing()} returns
     * {@code true} if and only if the string is {@code null} or empty.
     *
     * <p>This is the core statement of Property 10 for the invite sub-command:
     * the validation guard must accept exactly the set of null/empty strings as
     * "missing" arguments.</p>
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_inviteArgMissingBiconditional(
            @ForAll("nullOrEmptyOrNonEmptyString") String arg) {

        boolean isMissing = IsCommand.isInviteArgMissing(arg);
        boolean isNullOrEmpty = (arg == null || arg.isEmpty());

        if (isMissing != isNullOrEmpty) {
            throw new AssertionError(
                    "Property 10 violated: isInviteArgMissing() returned " + isMissing
                    + " but expected " + isNullOrEmpty
                    + " for arg='" + arg + "'. "
                    + "The predicate must return true iff the argument is null or empty.");
        }
    }

    /**
     * Biconditional property: for any string, {@code isKickArgMissing()} returns
     * {@code true} if and only if the string is {@code null} or empty.
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property
    void property10_kickArgMissingBiconditional(
            @ForAll("nullOrEmptyOrNonEmptyString") String arg) {

        boolean isMissing = IsCommand.isKickArgMissing(arg);
        boolean isNullOrEmpty = (arg == null || arg.isEmpty());

        if (isMissing != isNullOrEmpty) {
            throw new AssertionError(
                    "Property 10 violated: isKickArgMissing() returned " + isMissing
                    + " but expected " + isNullOrEmpty
                    + " for arg='" + arg + "'. "
                    + "The predicate must return true iff the argument is null or empty.");
        }
    }

    /**
     * Usage hints for invite and kick must be distinct (they reference different sub-commands).
     *
     * <p><b>Validates: Requirements 11.3</b></p>
     */
    @Property(tries = 1)
    void property10_usageHintsAreDistinct() {
        String inviteHint = IsCommand.inviteUsageHint();
        String kickHint = IsCommand.kickUsageHint();

        if (inviteHint.equals(kickHint)) {
            throw new AssertionError(
                    "Property 10 violated: invite and kick usage hints are identical. "
                    + "Each sub-command must have its own specific usage hint. Got: " + inviteHint);
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    /** Generates either {@code null} or an empty string {@code ""}. */
    @Provide
    Arbitrary<String> nullOrEmptyString() {
        return Arbitraries.of(null, "");
    }

    /** Generates non-null, non-empty strings (at least 1 character). */
    @Provide
    Arbitrary<String> nonEmptyString() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(30);
    }

    /**
     * Generates a mix of null, empty, and non-empty strings to cover the full
     * input space for the biconditional properties.
     */
    @Provide
    Arbitrary<String> nullOrEmptyOrNonEmptyString() {
        Arbitrary<String> nullOrEmpty = Arbitraries.of(null, "");
        Arbitrary<String> nonEmpty = Arbitraries.strings().ofMinLength(1).ofMaxLength(30);
        return Arbitraries.oneOf(nullOrEmpty, nonEmpty);
    }
}
