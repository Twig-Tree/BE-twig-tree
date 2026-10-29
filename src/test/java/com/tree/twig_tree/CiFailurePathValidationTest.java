package com.tree.twig_tree;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class CiFailurePathValidationTest {

	@Test
	void intentionallyFailsToValidateCiFailureReporting() {
		fail("Intentional failure for GitHub Actions CI validation (issue #113)");
	}
}
