package edu.ucsf.rbvi.structureViz2.internal.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.swing.DialogTaskManager;
import org.osgi.framework.BundleContext;

import edu.ucsf.rbvi.structureViz2.internal.tasks.CreateStructureNetworkTaskFactory;
import edu.ucsf.rbvi.structureViz2.internal.ui.AlignStructuresDialog;
import edu.ucsf.rbvi.structureViz2.internal.ui.ModelNavigatorDialog;

/**
 * This object maintains the relationship between Chimera objects and Cytoscape objects.
 */
// TODO: Listen for network view created/destroyed
public class StructureManager {
	static final String[] defaultStructureKeys = { "Structure", "pdb", "pdbFileName", "PDB ID",
			"structure", "biopax.xref.PDB", "pdb_ids" };
	static final String[] defaultChemStructKeys = { "Smiles", "smiles", "SMILES" };
	static final String[] defaultResidueKeys = { "FunctionalResidues", "ResidueList", "Residues" };

	public enum ModelType {
		PDB_MODEL, MODBASE_MODEL, SMILES
	};

	private BundleContext bundleContext = null;
	private ChimeraManager chimeraManager = null;
	private CreateStructureNetworkTaskFactory structureNetFactory = null;
	private Map<CyNetwork, StructureSettings> settings = null;
	private Map<CyIdentifiable, Set<ChimeraStructuralObject>> currentCyMap = null;
	private Map<ChimeraStructuralObject, Set<CyIdentifiable>> currentChimMap = null;
	private Map<CyIdentifiable, CyNetwork> networkMap = null;

	static private ModelNavigatorDialog mnDialog = null;
	static private AlignStructuresDialog alDialog = null;

	static private List<ChimeraStructuralObject> chimSelectionList;
	private boolean ignoreCySelection = false;

	public StructureManager(BundleContext bc) {
		this.bundleContext = bc;
		settings = new HashMap<CyNetwork, StructureSettings>();
		currentCyMap = new HashMap<CyIdentifiable, Set<ChimeraStructuralObject>>();
		currentChimMap = new HashMap<ChimeraStructuralObject, Set<CyIdentifiable>>();
		networkMap = new HashMap<CyIdentifiable, CyNetwork>();
		// Create the Chimera interface
		chimeraManager = new ChimeraManager(this);
		chimSelectionList = new ArrayList<ChimeraStructuralObject>();
	}

	public ChimeraManager getChimeraManager() {
		return chimeraManager;
	}

	public Object getService(Class<?> serviceClass) {
		return bundleContext.getService(bundleContext.getServiceReference(serviceClass.getName()));
	}

	public void setCreateStructureNetFactory(CreateStructureNetworkTaskFactory factory) {
		this.structureNetFactory = factory;
	}

	// TODO: rewrite
	public void openStructures(CyNetwork network, Map<CyIdentifiable, List<String>> chimObjNames,
			ModelType type) {
		if (network == null
				|| chimObjNames.size() == 0
				|| (!chimeraManager.isChimeraLaunched() && !chimeraManager
						.launchChimera(getChimeraPaths(network)))) {
			return;
		}
		// potential rins
		HashSet<CyNetwork> potentialRINs = new HashSet<CyNetwork>();
		// for each node that has an associated structure
		for (CyIdentifiable cyObj : chimObjNames.keySet()) {
			// save node to track its selection and mapping to chimera objects
			if (!currentCyMap.containsKey(cyObj)) {
				currentCyMap.put(cyObj, new HashSet<ChimeraStructuralObject>());
			}
			// save node to network mapping to keep track of selection events
			if (!networkMap.containsKey(cyObj)) {
				networkMap.put(cyObj, network);
			}
			// for each structure that has to be opened
			for (String chimObjName : chimObjNames.get(cyObj)) {
				// get or open the corresponding models if they already exist
				List<ChimeraModel> currentModels = chimeraManager.getChimeraModels(chimObjName, type);
				if (currentModels.size() == 0) {
					// open and return models
					currentModels = chimeraManager.openModel(chimObjName, type);
				}
				// for each model
				for (ChimeraModel currentModel : currentModels) {
					if (network.containsNode((CyNode) cyObj)
							&& network.getRow(cyObj).isSet(ChimUtils.RESIDUE_ATTR)) {
						ChimeraStructuralObject res = ChimUtils.fromAttribute(
								network.getRow(cyObj).get(ChimUtils.RESIDUE_ATTR, String.class), chimeraManager);
						if (res != null) {
							potentialRINs.add(network);
							continue;
						}
					}
					currentCyMap.get(cyObj).add(currentModel);
					if (!currentChimMap.containsKey(currentModel)) {
						currentChimMap.put(currentModel, new HashSet<CyIdentifiable>());
					}
					currentChimMap.get(currentModel).add(cyObj);
					currentModel.addCyObject(cyObj, network);
					currentModel.setFuncResidues(ChimUtils.parseFuncRes(getResidueList(network, cyObj),
							chimObjName));
				}
			}
		}
		for (CyNetwork net : potentialRINs) {
			addStructureNetwork(net);
		}
		if (mnDialog != null) {
			mnDialog.modelChanged();
		}
	}

	public void closeStructures(Map<CyIdentifiable, List<String>> chimObjNames) {
		Set<ChimeraModel> closedModels = new HashSet<ChimeraModel>();
		// for each cytoscape object and chimera model pair
		for (CyIdentifiable cyObj : chimObjNames.keySet()) {
			if (!currentCyMap.containsKey(cyObj)) {
				// should not be the case
				System.out.println("Could not find structure for cyObj");
				continue;
			}
			// get all chimera objects associated with the cytoscape object
			List<ChimeraStructuralObject> associatedChimObjects = new ArrayList<ChimeraStructuralObject>(
					currentCyMap.get(cyObj));
			for (ChimeraStructuralObject chimObject : associatedChimObjects) {
				// find the model to be closed
				if (chimObject instanceof ChimeraModel
						&& chimObjNames.get(cyObj).contains(((ChimeraModel) chimObject).getModelName())) {
					closedModels.add((ChimeraModel) chimObject);
				}
			}
		}
		// iterate over closed models and remove associations of residues in the model
		for (ChimeraModel model : closedModels) {
			closeModel(model);
		}
		if (mnDialog != null) {
			mnDialog.modelChanged();
		}
	}

	public void closeModel(ChimeraModel model) {
		// close model in Chimera
		chimeraManager.closeModel(model);
		// remove all associations
		if (currentChimMap.containsKey(model)) {
			for (CyIdentifiable cyObj : model.getCyObjects().keySet()) {
				if (cyObj == null) {
					continue;
				} else if (currentCyMap.containsKey(cyObj)) {
					currentCyMap.get(cyObj).remove(model);
				} else if (cyObj instanceof CyNetwork) {
					for (ChimeraResidue residue : model.getResidues()) {
						if (currentChimMap.containsKey(residue)) {
							for (CyIdentifiable cyObjRes : currentChimMap.get(residue)) {
								if (currentCyMap.containsKey(cyObjRes)) {
									currentCyMap.get(cyObjRes).remove(residue);
								}
							}
							currentChimMap.remove(residue);
						}
					}
				}
			}
			currentChimMap.remove(model);
		}
	}

	public void addStructureNetwork(CyNetwork rin) {
		if (rin == null) {
			return;
		}
		for (CyNode node : rin.getNodeList()) {
			networkMap.put(node, rin);
			String residueSpec = rin.getRow(node).get(ChimUtils.RESIDUE_ATTR, String.class);
			ChimeraResidue chimObj = (ChimeraResidue) ChimUtils
					.fromAttribute(residueSpec, chimeraManager);
			// chimObj.getChimeraModel().addCyObject(node, rin);
			if (chimObj == null) {
				continue;
			}
			chimObj.getChimeraModel().addCyObject(rin, rin);
			if (!currentCyMap.containsKey(node)) {
				currentCyMap.put(node, new HashSet<ChimeraStructuralObject>());
			}
			currentCyMap.get(node).add(chimObj);
			if (!currentChimMap.containsKey(chimObj)) {
				currentChimMap.put(chimObj, new HashSet<CyIdentifiable>());
			}
			currentChimMap.get(chimObj).add(node);
			// TODO: add rin network to model in currentChimMap?
			// TODO: add residues to model? see Structure.setResidueList
		}
	}

	public void exitChimera() {
		// exit chimera, invokes clearOnExitChimera
		if (mnDialog != null) {
			mnDialog.setVisible(false);
			mnDialog = null;
		}
		if (alDialog != null) {
			alDialog.setVisible(false);
		}
		chimeraManager.exitChimera();
	}

	// invoked by ChimeraManager whenever Chimera exits
	public void clearOnChimeraExit() {
		// clear structures
		currentCyMap.clear();
		currentChimMap.clear();
		networkMap.clear();
		if (mnDialog != null) {
			if (mnDialog.isVisible()) {
				mnDialog.lostChimera();
				mnDialog.setVisible(false);
			}
			mnDialog = null;
			if (alDialog != null) {
				alDialog.setVisible(false);
			}
		}
	}

	// We need to do this in two passes since some parts of a structure might be
	// selected and some might not. Our selection model (unfortunately) only tells
	// us that something has changed, not what...
	public void updateCytoscapeSelection() {
		// TODO: Shorten if possible
		// List<ChimeraStructuralObject> selectedChimObj
		ignoreCySelection = true;
		System.out.println("update Cytoscape selection");
		// find all possibly selected Cytoscape objects and unselect them
		Set<CyNetwork> networks = new HashSet<CyNetwork>();
		for (CyIdentifiable currentCyObj : currentCyMap.keySet()) {
			if (!networkMap.containsKey(currentCyObj)) {
				continue;
			}
			CyNetwork network = networkMap.get(currentCyObj);
			if (network == null) {
				continue;
			}
			if ((currentCyObj instanceof CyNode && network.containsNode((CyNode) currentCyObj))
					|| (currentCyObj instanceof CyEdge && network.containsEdge((CyEdge) currentCyObj))) {
				network.getRow(currentCyObj).set(CyNetwork.SELECTED, false);
				networks.add(network);
			}
		}

		// select only those associated with selected Chimera objects
		Set<CyIdentifiable> currentCyObjs = new HashSet<CyIdentifiable>();
		for (ChimeraStructuralObject chimObj : chimSelectionList) {
			ChimeraModel currentSelModel = chimObj.getChimeraModel();
			if (currentChimMap.containsKey(currentSelModel)) {
				currentCyObjs.addAll(currentChimMap.get(currentSelModel));
			}
			if (currentChimMap.containsKey(chimObj)) {
				currentCyObjs.addAll(currentChimMap.get(chimObj));
			}
		}
		for (CyIdentifiable cyObj : currentCyObjs) {
			if (cyObj == null || !networkMap.containsKey(cyObj)) {
				continue;
			}
			CyNetwork network = networkMap.get(cyObj);
			if (network == null) {
				continue;
			}
			if ((cyObj instanceof CyNode && network.containsNode((CyNode) cyObj))
					|| (cyObj instanceof CyEdge && network.containsEdge((CyEdge) cyObj))) {
				network.getRow(cyObj).set(CyNetwork.SELECTED, true);
				networks.add(network);
			}
		}

		CyNetworkViewManager cyNetViewManager = (CyNetworkViewManager) getService(CyNetworkViewManager.class);
		// Update network views
		for (CyNetwork network : networks) {
			Collection<CyNetworkView> views = cyNetViewManager.getNetworkViews(network);
			for (CyNetworkView view : views) {
				view.updateView();
			}
		}
		ignoreCySelection = false;
	}

	// TODO: Fix selection when ...
	public void cytoscapeSelectionChanged(Map<Long, Boolean> selectedRows) {
		if (ignoreCySelection || currentCyMap.size() == 0) {
			return;
		}
		// clearSelectionList();
		System.out.println("cytoscape selection changed");
		// iterate over all cy objects with associated models
		for (CyIdentifiable cyObj : currentCyMap.keySet()) {
			if (!selectedRows.containsKey(cyObj.getSUID())) {
				continue;
			}
			for (ChimeraStructuralObject chimObj : currentCyMap.get(cyObj)) {
				if (selectedRows.get(cyObj.getSUID())) {
					addChimSelection(chimObj);
					if (chimObj instanceof ChimeraResidue) {

						if (chimObj.getChimeraModel().isSelected()) {
							removeChimSelection(chimObj.getChimeraModel());
						} else if (chimObj.getChimeraModel().getChain(((ChimeraResidue) chimObj).getChainId())
								.isSelected()) {
							removeChimSelection(chimObj.getChimeraModel().getChain(
									((ChimeraResidue) chimObj).getChainId()));
						}
					}
				} else {
					removeChimSelection(chimObj);
					if (chimObj.hasSelectedChildren() && chimObj instanceof ChimeraModel) {
						for (ChimeraResidue residue : ((ChimeraModel) chimObj).getSelectedResidues()) {
							removeChimSelection(residue);
						}
					}
				}
			}
		}
		System.out.println("selection list: " + getChimSelectionCount());
		updateChimeraSelection();
		selectionChanged();
	}

	public void updateChimeraSelection() {
		System.out.println("update Chimera selection");
		String selSpec = "";
		for (int i = 0; i < chimSelectionList.size(); i++) {
			ChimeraStructuralObject nodeInfo = chimSelectionList.get(i);
			// we do not care about the model anymore
			selSpec = selSpec.concat(nodeInfo.toSpec());
			// TODO: Save models in a HashMap/Set for better performance?
			if (i < chimSelectionList.size() - 1)
				selSpec.concat("|");
		}
		if (selSpec.length() > 0) {
			chimeraManager.select("sel " + selSpec);
		} else {
			chimeraManager.select("~sel");
		}
	}

	/**
	 * This is called by the selectionListener to let us know that the user has changed their
	 * selection in Chimera. We need to go back to Chimera to find out what is currently selected and
	 * update our list.
	 */
	public void chimeraSelectionChanged() {
		System.out.println("Chimera selection changed");
		clearSelectionList();
		// Execute the command to get the list of models with selections
		Map<Integer, ChimeraModel> selectedModelsMap = chimeraManager.getSelectedModels();
		// Now get the residue-level data
		chimeraManager.getSelectedResidues(selectedModelsMap);
		// Get the selected objects
		try {
			for (ChimeraModel selectedModel : selectedModelsMap.values()) {
				int modelNumber = selectedModel.getModelNumber();
				int subModelNumber = selectedModel.getSubModelNumber();
				// Get the corresponding "real" model
				if (chimeraManager.hasChimeraModel(modelNumber, subModelNumber)) {
					ChimeraModel dataModel = chimeraManager.getChimeraModel(modelNumber, subModelNumber);
					if (dataModel.getResidueCount() == selectedModel.getResidueCount()
							|| dataModel.getModelType() == StructureManager.ModelType.SMILES) {
						// Select the entire model
						addChimSelection(dataModel);
						// dataModel.setSelected(true);
					} else {
						for (ChimeraChain selectedChain : selectedModel.getChains()) {
							ChimeraChain dataChain = dataModel.getChain(selectedChain.getChainId());
							if (selectedChain.getResidueCount() == dataChain.getResidueCount()) {
								addChimSelection(dataChain);
								// dataChain.setSelected(true);
							} else {
								// Need to select individual residues
								for (ChimeraResidue res : selectedChain.getResidues()) {
									String residueIndex = res.getIndex();
									ChimeraResidue residue = dataChain.getResidue(residueIndex);
									if (residue == null) {
										continue;
									}
									addChimSelection(residue);
									// residue.setSelected(true);
								} // resIter.hasNext
							}
						} // chainIter.hasNext()
					}
				}
			} // modelIter.hasNext()
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.out.println("selection list: " + getChimSelectionCount());
		// Finally, update the navigator panel
		selectionChanged();
		updateCytoscapeSelection();
	}

	public void selectFunctResidues(Collection<ChimeraModel> models) {
		clearSelectionList();
		for (ChimeraModel model : models) {
			for (ChimeraResidue residue : model.getFuncResidues()) {
				addChimSelection(residue);
			}
		}
		updateChimeraSelection();
		updateCytoscapeSelection();
		selectionChanged();
	}

	public List<ChimeraStructuralObject> getChimSelectionList() {
		return chimSelectionList;
	}

	public int getChimSelectionCount() {
		return chimSelectionList.size();
	}

	/**
	 * Add a selection to the selection list. This is called primarily by the Model Navigator Dialog
	 * to keep the selections in sync
	 * 
	 * @param selectionToAdd
	 *          the selection to add to our list
	 */
	public void addChimSelection(ChimeraStructuralObject selectionToAdd) {
		if (selectionToAdd != null && !chimSelectionList.contains(selectionToAdd)) {
			chimSelectionList.add(selectionToAdd);
			selectionToAdd.setSelected(true);
		}
	}

	/**
	 * Remove a selection from the selection list. This is called primarily by the Model Navigator
	 * Dialog to keep the selections in sync
	 * 
	 * @param selectionToRemove
	 *          the selection to remove from our list
	 */
	public void removeChimSelection(ChimeraStructuralObject selectionToRemove) {
		if (selectionToRemove != null && chimSelectionList.contains(selectionToRemove)) {
			chimSelectionList.remove(selectionToRemove);
			selectionToRemove.setSelected(false);
		}
	}

	/**
	 * Clear the list of selected objects
	 */
	public void clearSelectionList() {
		for (ChimeraStructuralObject cso : chimSelectionList) {
			if (cso != null)
				cso.setSelected(false);
		}
		chimSelectionList.clear();
	}

	// Network to be deleted
	public void updateMapping(CyNetwork network) {
		if (network == null) {
			return;
		}
		for (ChimeraStructuralObject chimObj : currentChimMap.keySet()) {
			// if (currentChimMap.get(chimObj).contains(network)) {
			// currentChimMap.get(chimObj).remove(network);
			// }
			if (chimObj instanceof ChimeraModel
					&& ((ChimeraModel) chimObj).getCyObjects().containsKey(network)) {
				System.out.println("remove network");
				((ChimeraModel) chimObj).getCyObjects().remove(network);
			}
		}
		for (CyNode node : network.getNodeList()) {
			if (currentCyMap.containsKey(node)) {
				for (ChimeraStructuralObject chimObj : currentCyMap.get(node)) {
					if (currentChimMap.containsKey(chimObj)) {
						currentChimMap.get(chimObj).remove(node);
					}
				}
				currentCyMap.remove(node);
			}
		}
		modelChanged();
	}

	/**
	 * Dump and refresh all of our model/chain/residue info
	 */
	// TODO: Fix!
	public void updateModels() {
		// Get a new model list
		// HashMap<Integer, ChimeraModel> newHash = new HashMap<Integer,
		// ChimeraModel>();

		// Stop all of our listeners while we try to handle this
		chimeraManager.stopListening();

		// Get all of the open models
		List<ChimeraModel> newModelList = chimeraManager.getModelList();

		// Match them up -- assume that the model #'s haven't changed
		for (ChimeraModel newModel : newModelList) {
			// Get the color (for our navigator)
			newModel.setModelColor(chimeraManager.getModelColor(newModel));

			// Get our model info
			int modelNumber = newModel.getModelNumber();
			int subModelNumber = newModel.getSubModelNumber();

			// If we already know about this model number, get the Structure,
			// which tells us about the associated CyNode
			if (chimeraManager.hasChimeraModel(modelNumber, subModelNumber)) {
				ChimeraModel oldModel = chimeraManager.getChimeraModel(modelNumber, subModelNumber);
				chimeraManager.removeChimeraModel(modelNumber, subModelNumber);
				newModel.setModelType(oldModel.getModelType());
				if (oldModel.getModelType() == ModelType.SMILES) {
					newModel.setModelName(oldModel.getModelName());
				}
				// re-assign associations to cytoscape objects
				Map<CyIdentifiable, CyNetwork> oldModelCyObjs = oldModel.getCyObjects();
				for (CyIdentifiable cyObj : oldModelCyObjs.keySet()) {
					// add cy objects to the new model
					newModel.addCyObject(cyObj, oldModelCyObjs.get(cyObj));
					if (currentCyMap.containsKey(cyObj)) {
						currentCyMap.get(cyObj).add(newModel);
						if (currentCyMap.get(cyObj).contains(oldModel)) {
							currentCyMap.get(cyObj).remove(oldModel);
						}
					}
				}
				// add new model to the chimera objects map and remove old model
				if (currentChimMap.containsKey(oldModel)) {
					currentChimMap.put(newModel, currentChimMap.get(oldModel));
					currentChimMap.remove(oldModel);
				}
			} else {
				// TODO: Associate new model with any node or network?
				// mapNewModel(newModel);
			}
			// add new model to ChimeraManager
			chimeraManager.addChimeraModel(modelNumber, subModelNumber, newModel);

			// Get the residue information
			if (newModel.getModelType() != ModelType.SMILES) {
				chimeraManager.getResidueInfo(newModel);
			}
			for (CyIdentifiable cyObj : newModel.getCyObjects().keySet()) {
				if (cyObj != null && cyObj instanceof CyNetwork) {
					addStructureNetwork((CyNetwork) cyObj);
				}
			}
		}

		// Restart all of our listeners
		chimeraManager.startListening();

		// Done
	}

	public ModelNavigatorDialog getModelNavigatorDialog() {
		return mnDialog;
	}

	public void launchModelNavigatorDialog() {
		if (mnDialog == null) {
			CySwingApplication cyApplication = (CySwingApplication) getService(CySwingApplication.class);
			mnDialog = ModelNavigatorDialog.LaunchModelNavigator(cyApplication.getJFrame(), this);
		}
		mnDialog.setVisible(true);
	}

	/**
	 * Invoked by the listener thread.
	 */
	public void modelChanged() {
		if (mnDialog != null) {
			mnDialog.modelChanged();
		}
	}

	/**
	 * Inform our interface that the selection has changed
	 */
	public void selectionChanged() {
		if (mnDialog != null)
			System.out.println("update dialog selection");
		mnDialog.updateSelection(new ArrayList<ChimeraStructuralObject>(chimSelectionList));
	}

	public void launchAlignDialog(boolean useChains) {
		if (alDialog != null) {
			alDialog.setVisible(false);
			alDialog.dispose();
		}
		List<ChimeraStructuralObject> chimObjectList = new ArrayList<ChimeraStructuralObject>();
		for (ChimeraModel model : chimeraManager.getChimeraModels()) {
			chimObjectList.add(model);
			if (useChains) {
				for (ChimeraChain chain : model.getChains()) {
					chimObjectList.add(chain);
				}
			}
		}
		// Bring up the dialog
		alDialog = new AlignStructuresDialog(mnDialog, this, chimObjectList);
		alDialog.pack();
		alDialog.setVisible(true);
	}

	public AlignStructuresDialog getAlignDialog() {
		return alDialog;
	}

	public void launchStructureNetworkDialog() {
		DialogTaskManager taskManager = (DialogTaskManager) getService(DialogTaskManager.class);
		if (taskManager != null) {
			taskManager.execute(structureNetFactory.createTaskIterator());
		}
	}

	/**
	 * Return all open structures for a set of CyObjects. Invoked by CloseStructuresTask.
	 * 
	 * @param cyObjSet
	 * @return
	 */
	public Map<CyIdentifiable, List<String>> getOpenChimObjNames(List<CyIdentifiable> cyObjSet) {
		Map<CyIdentifiable, List<String>> matchingNames = new HashMap<CyIdentifiable, List<String>>();
		for (CyIdentifiable cyObj : cyObjSet) {
			List<String> nodeMatchingNames = new ArrayList<String>();
			if (currentCyMap.containsKey(cyObj)) {
				for (ChimeraStructuralObject chimObj : currentCyMap.get(cyObj)) {
					if (chimObj instanceof ChimeraModel) {
						nodeMatchingNames.add(((ChimeraModel) chimObj).getModelName());
					}
				}
				if (nodeMatchingNames.size() > 0) {
					matchingNames.put(cyObj, nodeMatchingNames);
				}
			}
		}
		return matchingNames;
	}

	/**
	 * Return the names of structures or smiles that can be opened in Chimera from the selected
	 * attribute. Invoked by openStructuresTask.
	 * 
	 * @param network
	 * @param nodeSet
	 * @return
	 */
	public Map<CyIdentifiable, List<String>> getChimObjNames(CyNetwork network,
			List<CyIdentifiable> cyObjSet, ModelType type) {

		Map<CyIdentifiable, List<String>> mapChimObjNames = new HashMap<CyIdentifiable, List<String>>();
		if (network == null || cyObjSet.size() == 0)
			return mapChimObjNames;
		CyTable table = null;
		if (cyObjSet.get(0) instanceof CyNode) {
			table = network.getDefaultNodeTable();
		} else if (cyObjSet.get(0) instanceof CyEdge) {
			table = network.getDefaultEdgeTable();
		}
		if (table == null) {
			return mapChimObjNames;
		}
		List<String> attrsFound = null;
		if (type == ModelType.PDB_MODEL)
			attrsFound = CyUtils.getMatchingAttributes(table, getCurrentStructureKeys(network));
		else if (type == ModelType.SMILES) {
			attrsFound = CyUtils.getMatchingAttributes(table, getCurrentChemStructKeys(network));
		}

		// if something is null, just return an empty map
		if (attrsFound == null || attrsFound.size() == 0)
			return mapChimObjNames;
		// iterate over cytoscape objects
		for (CyIdentifiable cyObj : cyObjSet) {
			// skip if node/edge does not exist anymore
			if (!table.rowExists(cyObj.getSUID())) {
				continue;
			}
			CyRow row = table.getRow(cyObj.getSUID());
			// iterate over attributes that contain structures
			for (String column : attrsFound) {
				CyColumn col = table.getColumn(column);

				Class<?> colType = col.getType();
				List<String> cellList = new ArrayList<String>();
				if (colType == String.class) {
					String cell = row.get(column, String.class, "").trim();
					if (cell.equals("")) {
						continue;
					}
					String[] cellArray = cell.split(",");
					for (String str : cellArray) {
						cellList.add(str.trim());
					}
				} else if (colType == List.class && col.getListElementType() == String.class) {
					for (String str : row.getList(column, String.class)) {
						cellList.add(str.trim());
					}
				} else {
					continue;
				}

				for (String cell : cellList) {
					// skip if the structure is already open
					if (currentCyMap.containsKey(cyObj) && chimeraManager.getChimeraModels(cell).size() > 0) {
						continue;
					}
					// add strcture name to map
					if (!mapChimObjNames.containsKey(cyObj)) {
						mapChimObjNames.put(cyObj, new ArrayList<String>());
					}
					mapChimObjNames.get(cyObj).add(cell);
				}
			}
		}
		return mapChimObjNames;
	}

	public void setStructureSettings(CyNetwork network, StructureSettings newSettings) {
		settings.put(network, newSettings);
	}

	public List<String> getAllStructureKeys(CyNetwork network) {
		return Arrays.asList(defaultStructureKeys);
	}

	public List<String> getCurrentStructureKeys(CyNetwork network) {
		if (!settings.containsKey(network))
			return Arrays.asList(defaultStructureKeys);
		return settings.get(network).getStructureColumns().getSelectedValues();
	}

	public List<String> getAllChemStructKeys(CyNetwork network) {
		return Arrays.asList(defaultChemStructKeys);
	}

	public List<String> getCurrentChemStructKeys(CyNetwork network) {
		if (!settings.containsKey(network))
			return Arrays.asList(defaultChemStructKeys);
		return settings.get(network).getChemStructureColumns().getSelectedValues();
	}

	public List<String> getAllResidueKeys(CyNetwork network) {
		return Arrays.asList(defaultResidueKeys);
	}

	public List<String> getCurrentResidueKeys(CyNetwork network) {
		if (!settings.containsKey(network))
			return Arrays.asList(defaultResidueKeys);
		return settings.get(network).getResidueColumns().getSelectedValues();
	}

	public List<String> getChimeraPaths(CyNetwork network) {
		List<String> pathList = new ArrayList<String>();

		if (settings.containsKey(network)) {
			String path = settings.get(network).getChimeraPath();
			if (path != null) {
				pathList.add(path);
				return pathList;
			}
		}

		String os = System.getProperty("os.name");
		if (os.startsWith("Linux")) {
			pathList.add("/usr/local/chimera/bin/chimera");
			pathList.add("/usr/local/bin/chimera");
			pathList.add("/usr/bin/chimera");
		} else if (os.startsWith("Windows")) {
			pathList.add("\\Program Files\\Chimera\\bin\\chimera");
			pathList.add("C:\\Program Files\\Chimera\\bin\\chimera.exe");
		} else if (os.startsWith("Mac")) {
			pathList.add("/Applications/Chimera.app/Contents/MacOS/chimera");
		}

		return pathList;
	}

	/**
	 * Set the "active site" or "special" residues
	 * 
	 * @param residues
	 *          String representation of the residues (comma separated)
	 */
	private List<String> getResidueList(CyNetwork network, CyIdentifiable cyObj) {
		List<String> residueList = new ArrayList<String>();
		// Get from attribute
		CyTable nodeTable = network.getDefaultNodeTable();
		List<String> attrsFound = CyUtils.getMatchingAttributes(nodeTable,
				getCurrentResidueKeys(network));
		if (attrsFound == null || attrsFound.size() == 0 || !nodeTable.rowExists(cyObj.getSUID())) {
			return residueList;
		}
		CyRow row = nodeTable.getRow(cyObj.getSUID());
		// iterate over attributes that contain residues
		for (String column : attrsFound) {
			CyColumn col = nodeTable.getColumn(column);
			Class<?> colType = col.getType();
			if (colType == String.class) {
				String cell = row.get(column, String.class, "").trim();
				if (cell.equals("")) {
					continue;
				}
				String[] cellArray = cell.split(",");
				for (String str : cellArray) {
					residueList.add(str.trim());
				}
			} else if (colType == List.class && col.getListElementType() == String.class) {
				for (String str : row.getList(column, String.class)) {
					residueList.add(str.trim());
				}
			} else {
				continue;
			}
		}
		return residueList;
	}
}
