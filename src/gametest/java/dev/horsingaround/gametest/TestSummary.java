package dev.horsingaround.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Runs last: fails the run if any test failed. The tests record failures here instead of throwing, so one failing test
 * doesn't keep the others from running.
 */
public final class TestSummary implements FabricClientGameTest {
	private static final StringBuilder FAILED = new StringBuilder();

	static void failed(final int failures, final String report) {
		if (failures > 0) {
			FAILED.append("\n  ").append(failures).append(" failed checks, see ").append(report);
		}
	}

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!FAILED.isEmpty()) {
			throw new AssertionError("Client tests failed:" + FAILED);
		}
	}
}
