package io.openems.edge.controller.ess.inteligentcontroller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.DateUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
	name = "Controller.InteligentPrediction", //
	immediate = true, //
	configurationPolicy = ConfigurationPolicy.REQUIRE//
)
public class ControllerEssIntrligentControllerImpl extends AbstractOpenemsComponent
	implements ControllerEssInteligentController, Controller, OpenemsComponent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    private final Logger log = LoggerFactory.getLogger(ControllerEssIntrligentControllerImpl.class);

    @Reference
    private ComponentManager componentManager;

    private Config config;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime slowStartTime;
    private int slowforceChargeMinutes;
    private ChargeState chargeState = ChargeState.NORMAL;

    public ControllerEssIntrligentControllerImpl() {
	super(//
		OpenemsComponent.ChannelId.values(), //
		Controller.ChannelId.values(), //
		ControllerEssInteligentController.ChannelId.values() //
	);
    }

    @Activate
    private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
	super.activate(context, config.id(), config.alias(), config.enabled());
	this.applyConfig(config);
    }

    @Modified
    private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
	super.modified(context, config.id(), config.alias(), config.enabled());
	this.applyConfig(config);

    }

    @Override
    @Deactivate
    protected void deactivate() {
	super.deactivate();
    }

    private void applyConfig(Config config) throws OpenemsNamedException {
	// TODO switch format to {@link DateTimeFormatter#ISO_LOCAL_DATE}
	
	this.config = config;
    }

    @Override
    public void run() throws OpenemsNamedException {
	ManagedSymmetricEss ess = this.componentManager.getComponent(this.config.ess());
	ElectricityMeter meter = this.componentManager.getComponent(this.config.meter_id());

	var power = this.getPower(ess, meter);
	this.applyPower(ess, power);
    }

    /**
     * Applies the power on the ESS.
     *
     * @param ess         {@link ManagedSymmetricEss} where the power needs to be
     *                    set
     * @param activePower the active power
     * @throws OpenemsNamedException on error
     */
    private void applyPower(ManagedSymmetricEss ess, Integer activePower) throws OpenemsNamedException {
	if (activePower != null) {
	    ess.setActivePowerEqualsWithPid(activePower);
	    this.channel(ControllerEssInteligentController.ChannelId.CALCULATED_POWER).setNextValue(activePower);
	}
    }

    /**
     * Gets the current ActivePower.
     *
     * @param ess   the {@link ManagedSymmetricEss}
     * @param meter the {@link ElectricityMeter}
     * @return the currently valid active power, or null to set no power
     * @throws IllegalArgumentException on error
     * @throws OpenemsException         on error
     */
    private Integer getPower(ManagedSymmetricEss ess, ElectricityMeter meter)
	    throws OpenemsException, IllegalArgumentException {

	var now = LocalDateTime.now(this.componentManager.getClock());

	boolean stateChanged;
	Integer power = null;

	do {
	    stateChanged = false;
		power = this.calculatePeakShavePower(ess, meter);
		
	    
	} while (stateChanged); // execute again if the state changed

	// store current state in StateMachine channel
	this.channel(ControllerEssInteligentController.ChannelId.STATE_MACHINE).setNextValue(this.chargeState);
	return power;

    }


    /**
     * This method calculates the power that is required to cut the peak during time
     * slot.
     *
     * @param ess   the {@link ManagedSymmetricEss}
     * @param meter the {@link ElectricityMeter} of the grid
     * @return active power to be set on the ESS
     * @throws InvalidValueException on error
     */
    private int calculatePeakShavePower(ManagedSymmetricEss ess, ElectricityMeter meter) throws InvalidValueException {
	/*
	 * Check that we are On-Grid (and warn on undefined Grid-Mode)
	 */
	var gridMode = ess.getGridMode();
	switch (gridMode) {
	case UNDEFINED:
	    this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
	    break;
	case ON_GRID:
	    break;
	case OFF_GRID:
	    return 0;
	}

	// Calculate 'real' grid-power (without current ESS charge/discharge)
	var gridPower = meter.getActivePower().getOrError() /* current buy-from/sell-to grid */
		+ ess.getActivePower().getOrError() /* current charge/discharge ESS */;

	int calculatedPower;
	calculatedPower = 0;
	this.channel(ControllerEssInteligentController.ChannelId.PEAK_SHAVED_POWER).setNextValue(calculatedPower);
	return calculatedPower;
    }

    /**
     * Converts a string to a LocalTime.
     *
     * @param time the time as a string
     * @return the converted time
     */
    protected static LocalTime convertTime(String time) throws OpenemsException {
	// TODO switch format to {@link DateTimeFormatter#ISO_LOCAL_TIME}
	return DateUtils.parseLocalTimeOrError(time, TIME_FORMATTER);
    }



}