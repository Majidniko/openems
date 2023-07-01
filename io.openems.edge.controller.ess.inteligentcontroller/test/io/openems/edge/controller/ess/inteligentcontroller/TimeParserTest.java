package io.openems.edge.controller.ess.inteligentcontroller;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.controller.ess.inteligentcontroller.ControllerEssIntrligentControllerImpl;

public class TimeParserTest {

	@Test
	public void test() throws OpenemsException {
		String startTime = "23:30";
		LocalTime time = LocalTime.of(23, 30);
		assertEquals(time, ControllerEssIntrligentControllerImpl.convertTime(startTime));

	}

	@Test
	public void testFormat() throws OpenemsException {
		String startTime = "9:00";
		LocalTime time = LocalTime.of(9, 00);
		assertEquals(time, ControllerEssIntrligentControllerImpl.convertTime(startTime));

	}

	@Test
	public void testFormat1() throws OpenemsException {
		String startTime = "09:00";
		LocalTime time = LocalTime.of(9, 00);
		assertEquals(time, ControllerEssIntrligentControllerImpl.convertTime(startTime));

	}

	@Test(expected = OpenemsException.class)
	public void testWrongFormat() throws OpenemsException {
		ControllerEssIntrligentControllerImpl.convertTime(".9:00");
	}

	@Test(expected = OpenemsException.class)
	public void testWrongFormat1() throws OpenemsException {
		ControllerEssIntrligentControllerImpl.convertTime("55:00");
	}

}
