package edu.ucsf.rbvi.structureViz2.internal.tasks;

import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import edu.ucsf.rbvi.structureViz2.internal.model.StructureManager;

public class LaunchChimeraTask extends AbstractTask {

	private StructureManager structureManager;

	public LaunchChimeraTask(StructureManager structureManager) {
		this.structureManager = structureManager;
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("Launch Chimera");
		taskMonitor.setStatusMessage("Launching Chimera ...");
		CyApplicationManager cyAppManager = (CyApplicationManager) structureManager
				.getService(CyApplicationManager.class);
		List<String> pathList = structureManager.getChimeraPaths(cyAppManager.getCurrentNetwork());

		structureManager.getChimeraManager().launchChimera(pathList);
		if (structureManager.getChimeraManager().isChimeraLaunched()) {
			structureManager.launchModelNavigatorDialog();
		}
	}

}
