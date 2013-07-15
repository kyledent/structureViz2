package edu.ucsf.rbvi.structureViz2.internal.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

import edu.ucsf.rbvi.structureViz2.internal.model.StructureManager;
import edu.ucsf.rbvi.structureViz2.internal.model.StructureManager.ModelType;

public class OpenStructureFileTask extends AbstractTask {

	private StructureManager structureManager;
	private CyNetworkView netView;

	// TODO: [!] Should be a file tunable
	// @Tunable(description = "Structure file", params = "fileCategory=unspecified;input=true")
	@Tunable(description = "Structure file")
	public String structureFile = null;

	public OpenStructureFileTask(StructureManager structureManager, CyNetworkView netView) {
		this.structureManager = structureManager;
		this.netView = netView;
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		if (structureFile == null) {
			return;
		}
		taskMonitor.setTitle("Open Structure from File");
		taskMonitor.setStatusMessage("Opening structures ...");
		Map<CyIdentifiable, List<String>> structuresToOpen = new HashMap<CyIdentifiable, List<String>>();
		List<String> structures = new ArrayList<String>();
		// structureFile.getAbsolutePath()
		structures.add(structureFile);
		structuresToOpen.put(netView.getModel(), structures);
		structureManager.openStructures(netView.getModel(), structuresToOpen, ModelType.PDB_MODEL);

		// open dialog
		if (structureManager.getChimeraManager().isChimeraLaunched()) {
			structureManager.launchModelNavigatorDialog();
		}

	}

	@ProvidesTitle
	public String getTitle() {
		return "Open structure from file";
	}
}
