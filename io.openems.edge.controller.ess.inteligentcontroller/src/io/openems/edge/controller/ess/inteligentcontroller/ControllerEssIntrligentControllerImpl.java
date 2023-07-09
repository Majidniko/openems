package io.openems.edge.controller.ess.inteligentcontroller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
import io.openems.edge.meter.api.ElectricityMeter;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

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
	boolean once=true;
	ManagedSymmetricEss ess = this.componentManager.getComponent(this.config.ess());
	ElectricityMeter meter = this.componentManager.getComponent(this.config.meter_id());
if(once) {
	var power = this.getPower(ess, meter);
	String attributeName="PV";

	 this.logWarn(this.log, "ess active power " + ess.getActivePower().toString());
	 this.csvSplitter("p.p", attributeName);
	 String prepad = "Process\\" + attributeName;
	 this.convert(prepad + "part1.csv");
	 this.convert(prepad + "part2.csv");
	 this.replaceAttribute(prepad + "part2.arff", attributeName, "?");
	 this.train(prepad + "part1.arff", attributeName);
	 this.predicting(prepad + "part2.arff", attributeName,"Results\\" + attributeName + "Prediction output.arff");
	 once=!once;
}
	 
    }

    /**
     * Split information to have two different file  one for train and one for prediction.
     *
     * @param ess         {@link ManagedSymmetricEss} where the power needs to be
     *                    set
     * @return the currently valid active power, or null to set no power
     * @throws IllegalArgumentException on error
     * @throws OpenemsException         on error
     */
    private void csvSplitter(String path, String attribute)
	    throws OpenemsException, IllegalArgumentException {
        // Set the paths of the input and output files
        String inputFilePath = path;
        System.out.println(path);
        String prepad = "Process\\" + attribute;
        String outputFilePath1 = prepad + "part1.csv";
        String outputFilePath2 = prepad + "part2.csv";

        // Set the percentage of rows to be saved in each output file
        double percentage1 = 0.6; // 60%
        double percentage2 = 0.4; // 40%

        try {
            // Read the input CSV file
            BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
            List<String> lines = new ArrayList<String>();
            String line;
            String attributeline;
            attributeline = (line = reader.readLine());
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            // Calculate the number of lines to be saved in each output file
            int numLines1 = (int) Math.round(lines.size() * percentage1);
            int numLines2 = (int) Math.round(lines.size() * percentage2);

            // Write the lines to the output files
            BufferedWriter writer1 = new BufferedWriter(new FileWriter(outputFilePath1));
            writer1.write((attributeline));
            writer1.newLine();
            for (int i = 0; i < numLines1; i++) {
                writer1.write(lines.get(i));
                writer1.newLine();
            }
            writer1.close();

            BufferedWriter writer2 = new BufferedWriter(new FileWriter(outputFilePath2));
            writer2.write((attributeline));
            writer2.newLine();
            for (int i = numLines1; i < numLines1 + numLines2; i++) {
                writer2.write(lines.get(i));
                writer2.newLine();
            }
            writer2.close();

            // Print a message indicating the process is complete
            this.logWarn(this.log, "CSV file split successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Split information to have two different file  one for train and one for prediction.
     *
     * @param ess         {@link ManagedSymmetricEss} where the power needs to be
     *                    set
     * @return the currently valid active power, or null to set no power
     * @throws IllegalArgumentException on error
     * @throws OpenemsException         on error
     */
    
    private void convert(String path)  {
try {
        String Path = path;

        // Load the input file
        File inputFile = new File(Path);
        String fileName = inputFile.getPath();

        // Check if the file is in .csv format
        if (fileName.endsWith(".csv")) {
            CSVLoader loader = new CSVLoader();
            loader.setSource(inputFile);
            Instances data = loader.getDataSet();

            // Save the file in .arff format
            ArffSaver saver = new ArffSaver();
            saver.setInstances(data);
            saver.setFile(new File(fileName.replace(".csv", ".arff")));
            saver.writeBatch();
        } else {
            this.logWarn(this.log,"Error: Unsupported file format");
        }

        this.logInfo(this.log,"File converted successfully");
} catch (Exception e) {
    this.logWarn(this.log,e.toString());
}
    }

    private void replaceAttribute(String path, String attributeName, String newValue)   {
try {
        // Load dataset
        Instances data = new Instances(new BufferedReader(new FileReader(path)));

        // Specify the attribute index (e.g., 0 for the first attribute)
        int attributeIndex = 0;
        boolean loop = true;

        for (int i = 0; i < data.numAttributes(); i++) {
            if ((data.instance(i).attribute(i).name().compareTo(attributeName) == 0) && loop) {
                attributeIndex = i;
                loop = false;

                // Loop through the instances and replace the old value with the new value
                for (int j = 0; j < data.numInstances(); j++) {
                    data.instance(j).setMissing(attributeIndex);
                }
                // Save the modified dataset to a new ARFF file
                FileWriter writer = new FileWriter(path);
                writer.write(data.toString());
                writer.close();
            }

        }
        if (!loop) {
            this.logWarn(this.log,attributeName + " Values Replaced with " + newValue);
        } else {
            this.logWarn(this.log,attributeName + " attribute did not found in file...");
        }
    }catch(Exception e) {
	this.logWarn(this.log,e.toString());
    }
    }
    private void train(String path, String attributeName) {
        long startTime = System.nanoTime();
        try {
            randomForest(loadArff(path), attributeName);

            timer(startTime);
        } catch (Exception e) {
            this.logWarn(this.log,e.toString());
        }
    }
    private Instances loadArff(String path) {
        // Load dataset from ARFF file
        ArffLoader loader = new ArffLoader();
        try {
            loader.setFile(new File(path));
            return loader.getDataSet();
        } catch (IOException e) {
            this.logWarn(this.log,e.toString());
            return null;
        }
    }
    private void randomForest(Instances data, String attributeName) {
        for (int i = 0; i < data.numAttributes(); i++) {
            if ((data.instance(i).attribute(i).name().compareTo(attributeName) == 0)) {
                data.setClassIndex(i);
                try {
                    // Train J48 decision tree model
                   J48 randomForest = new J48();
                    randomForest.buildClassifier(data);
                    modelSaver(randomForest);
                } catch (Exception e) {
                    this.logWarn(this.log,e.toString());
                }
            }

        }
    }
    private void modelSaver(J48 randomForest) throws FileNotFoundException, Exception {
        String filename = "my_model.model";
        SerializationHelper.write(new FileOutputStream(filename), randomForest);
        this.logInfo(this.log,"Model saved in: " + filename);
    }
    public void timer(long startTime) {
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        long minutes = (elapsedTime / 1_000_000_000) / 60;
        long seconds = (elapsedTime / 1_000_000_000) % 60;
        long milliseconds = (elapsedTime % 1_000_000) / 1_000;
        String stringTime = (String.format("Elapsed time: %d minutes, %d seconds, %d milliseconds",
                minutes, seconds, milliseconds));
        this.logInfo(this.log,stringTime);

    }
    private void predicting(String path, String attributeName, String outputPath) {
        long startTime = System.nanoTime();
        try {

            resultSaver(prediction(loadArff(path), attributeName), outputPath);
            timer(startTime);
        } catch (Exception e) {
            this.logWarn(this.log,e.toString());
        }
    }
    private Instances prediction(Instances predictData, String attributeName) {
        try {
            J48 randomForest = new J48();
            randomForest = modelLoader();
            for (int i = 0; i < predictData.numAttributes(); i++) {
                if ((predictData.instance(i).attribute(i).name().compareTo(attributeName) == 0)) {
                    predictData.setClassIndex(i);
                    String message1 = "this is index : " + predictData.attribute(i);
                    this.logWarn(this.log,message1);

                    // Predict the exact attribute value for a given instance
                    for (int j = 0; j < predictData.numInstances(); j++) {
                        double prediction = randomForest.classifyInstance(predictData.instance(j));
                        double timeOfHappen = (predictData.instance(j).value(0));
                        // String message2 = ("Predicted value for " + attributeName + ": " + prediction
                        // + " will be in "
                        // + convertTime(timeOfHappen));
                        // logger.info(message2);
                        predictData.instance(j).setValue(predictData.classIndex(), prediction);
                    }
                }

            }
        } catch (Exception e) {
            this.logWarn(this.log,e.toString());
        }
        return predictData;
    }
    private J48 modelLoader() throws FileNotFoundException, Exception {

        // Load the saved Weka model from a file
        String filename = "my_model.model";
        return (J48) SerializationHelper.read(new FileInputStream(filename));
    }
    private void resultSaver(Instances predictData, String outputPath) {
        try {
            // Output the updated dataset as CSV
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
            writer.write(predictData.toString());
            writer.flush();
            writer.close();
        } catch (Exception e) {
            this.logWarn(this.log,"Error in writing results to file ! "+ e.toString());

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


	 this.logWarn(this.log, "meter get power "+ meter.getActivePower().toString());
	 ess._setActiveChargeEnergy(-5000);
	 this.logWarn(this.log, "ess get power "+ess.getActivePower().toString());
	 this.logWarn(this.log, "ess get capacity "+ess.getCapacity().toString());
	 this.logWarn(this.log, "ess get allow chage power "+ess.getActiveChargeEnergy().toString());
	 this.logWarn(this.log, "ess get soc power "+ess.getSoc().toString());
	 if(this.isEnabled()==true) {
		 this.logWarn(log, "is enable");
		 this.logInfo(log, LocalTime.now().toString());
	 }
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