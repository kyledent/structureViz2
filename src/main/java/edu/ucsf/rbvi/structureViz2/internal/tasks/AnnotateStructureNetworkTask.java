package edu.ucsf.rbvi.structureViz2.internal.tasks;

import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.util.ListMultipleSelection;

import edu.ucsf.rbvi.structureViz2.internal.model.RINManager;
import edu.ucsf.rbvi.structureViz2.internal.model.StructureManager;

public class AnnotateStructureNetworkTask extends AbstractTask {

	private StructureManager structureManager;
	private RINManager rinManager;
	private CyNetwork net;

	@Tunable(description = "Available residue attributes")
	public ListMultipleSelection<String> residueAttributes = new ListMultipleSelection<String>("");

	@Tunable(description = "Network for the selected nodes/edges", context = "nogui")
	public CyNetwork network;

	public AnnotateStructureNetworkTask(StructureManager structureManager, CyNetwork aNetwork) {
		this.structureManager = structureManager;
		this.rinManager = structureManager.getRINManager();
		net = aNetwork;
		List<String> attrs = structureManager.getAllChimeraResidueAttributes();
		residueAttributes = new ListMultipleSelection<String>(attrs);
		residueAttributes.setSelectedValues(attrs);
	}

	@ProvidesTitle
	public String getTitle() {
		return "Annotations Options";
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("Annotating Residue Interaction Network");
		// System.out.println("Annotating Residue Interaction Network");
		try {
		if (net == null) {
			CyNetwork current = net = ((CyApplicationManager) structureManager
					.getService(CyApplicationManager.class)).getCurrentNetwork();
			if (network != null) {
				net = network;
			} else if (current != null) {
				net = current;
			} else {
				taskMonitor.setStatusMessage("No network found, aborting...");
				// System.out.println("No network found, aborting...");
				return;
			}
		}
		if (residueAttributes != null && residueAttributes.getSelectedValues().size() > 0) {
			// System.out.println("Getting attribute data from Chimera ...");
			taskMonitor.setStatusMessage("Getting attribute data from Chimera ...");
			for (String resAttr : residueAttributes.getSelectedValues()) {
				// System.out.println("Getting data for attribute " + resAttr + " ...");
				taskMonitor.setStatusMessage("Getting data for attribute " + resAttr + " ...");
				if (resAttr.equals("SecondaryStructure")) {
					rinManager.annotateSS(net);
				} else if (resAttr.equals("Coordinates")) {
					rinManager.annotateCoord(net, "resCoord");
				} else {
					rinManager.annotate(net, resAttr, resAttr);
				}
			}
		} else {
			// System.out.println("No input found, aborting...");
			taskMonitor.setStatusMessage("No input found, aborting...");
		}
		} catch (Exception e) { e.printStackTrace(); }
	}

	// TODO: [Optional] How could we return annotations?
	// public Object getResults(Class expectedClass) {
	// if (expectedClass.equals(String.class)) {
	// return "Finished";
	// }
	// return true;
	// }
}
