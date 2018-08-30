package io.openems.edge.meter.eastron.sdm630;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import io.openems.edge.meter.api.MeterType;

@ObjectClassDefinition(
		name = "Meter Eastron SDM 630", //
		description = "Implements the Eastron SDM630 meter.")
@interface Config {
	String service_pid();

	String id() default "meter0";

	boolean enabled() default true;

	@AttributeDefinition(name = "Meter-Type", description = "Grid (default), Production, Consumption")
	MeterType type() default MeterType.GRID;

	@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus brige.")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "Modbus Unit-ID", description = "The Unit-ID of the Modbus device.")
	int modbusUnitId() default 1;

	@AttributeDefinition(name = "Modbus target filter", description = "This is auto-generated by 'Modbus-ID'.")
	String Modbus_target() default "";

	@AttributeDefinition(name = "Minimum Ever Active Power", description = "This is automatically updated.")
	int minActivePower();

	@AttributeDefinition(name = "Maximum Ever Active Power", description = "This is automatically updated.")
	int maxActivePower();

	String webconsole_configurationFactory_nameHint() default "Meter Eastron SDM 630 [{id}]";
}