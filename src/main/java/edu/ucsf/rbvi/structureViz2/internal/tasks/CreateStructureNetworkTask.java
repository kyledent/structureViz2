package edu.ucsf.rbvi.structureViz2.internal.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyEdge.Type;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.task.NetworkViewTaskFactory;
import org.cytoscape.task.visualize.ApplyPreferredLayoutTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.TunableSetter;
import org.cytoscape.work.util.ListMultipleSelection;
import org.cytoscape.work.util.ListSingleSelection;

import edu.ucsf.rbvi.structureViz2.internal.model.ChimUtils;
import edu.ucsf.rbvi.structureViz2.internal.model.ChimeraChain;
import edu.ucsf.rbvi.structureViz2.internal.model.ChimeraManager;
import edu.ucsf.rbvi.structureViz2.internal.model.ChimeraModel;
import edu.ucsf.rbvi.structureViz2.internal.model.ChimeraResidue;
import edu.ucsf.rbvi.structureViz2.internal.model.ChimeraStructuralObject;
import edu.ucsf.rbvi.structureViz2.internal.model.StructureManager;

// TODO: Bug: different number of nodes and edges in consecutive runs
// TODO: Add combined edges
// TODO: Bug: Selection disappears sometimes upon RIN creation
// TODO: Move methods to a manager class and create single tasks
// TODO: Add menus for adding each type of edges to an already existing network?
public class CreateStructureNetworkTask extends AbstractTask {

	@Tunable(description = "Name of new network", groups = "General")
	public String networkName;

	@Tunable(description = "Include interactions", groups = "General")
	public ListSingleSelection<String> includeInteracions;

	@Tunable(description = "Add hydrogens", groups = "General")
	public boolean addHydrogens;

	@Tunable(description = "Ignore water", groups = "General")
	public boolean ignoreWater;

	@Tunable(description = "Create combined edges", groups = "General")
	public boolean createCombiEdges;

	@Tunable(description = "Include contacts", groups = "Contacts")
	public boolean includeContacts;

	@Tunable(description = "Overlap cutoff", groups = "Contacts", dependsOn = "includeContacts=true")
	public double overlapCutoffCont;

	@Tunable(description = "HBond allowance", groups = "Contacts", dependsOn = "includeContacts=true")
	public double hbondAllowanceCont;

	@Tunable(description = "Bond separation", groups = "Contacts", dependsOn = "includeContacts=true")
	public int bondSepCont;

	@Tunable(description = "Include clashes", groups = "Clashes")
	public boolean includeClashes;

	@Tunable(description = "Overlap cutoff", groups = "Clashes", dependsOn = "includeClashes=true")
	public double overlapCutoffClash;

	@Tunable(description = "HBond allowance", groups = "Clashes", dependsOn = "includeClashes=true")
	public double hbondAllowanceClash;

	@Tunable(description = "Bond separation", groups = "Clashes", dependsOn = "includeClashes=true")
	public int bondSepClash;

	@Tunable(description = "Include hydrogen bonds", groups = "Hydrogen bonds")
	public boolean includeHBonds;

	@Tunable(description = "Remove redundant contacts", groups = "Hydrogen bonds", dependsOn = "includeHBonds=true")
	public boolean removeRedContacts;

	@Tunable(description = "Add tolerances to strict criteria", groups = "Hydrogen bonds", dependsOn = "includeHBonds=true")
	public boolean relaxHBonds;

	@Tunable(description = "Distance tolerance", groups = "Hydrogen bonds", dependsOn = "relaxHBonds=true")
	public double distSlop;

	@Tunable(description = "Angle tolerance", groups = "Hydrogen bonds", dependsOn = "relaxHBonds=true")
	public double angleSlop;

	@Tunable(description = "Include connectivity", groups = "Connectivity")
	public boolean includeConnectivity;

	@Tunable(description = "Include distances between CA atoms", groups = "Distance")
	public boolean includeDistance;

	@Tunable(description = "Distance cutoff (in angstoms)", groups = "Distance")
	public double distCutoff;

	// @Tunable(description =
	// "Calculate connectivity distances (more time consuming)", groups =
	// "Connectivity", dependsOn = "includeConnectivity=true")
	// public boolean includeConnectivityDistance;

	private static final String[] interactionArray = { "Within selection",
			"Between selection and other atoms", "All of the above" };

	private static final String HBONDEDGE = "hbond";
	private static final String CONTACTEDGE = "contact";
	private static final String CLASHEDGE = "clash";
	private static final String COMBIEDGE = "combi";
	private static final String DISTEDGE = "distance";

	// Edge attributes
	private static final String DISTANCE_ATTR = "Distance";
	private static final String OVERLAP_ATTR = "Overlap";
	private static final String INTSUBTYPE_ATTR = "InteractionSubtype";
	private static final String INTATOMS_ATTR = "InteractingAtoms";
	private static final String NUMINT_ATTR = "NumberInteractions";
	// Node attributes
	private static final String SMILES_ATTR = "SMILES";
	private static final String STRUCTURE_ATTR = "pdbFileName";
	private static final String SEED_ATTR = "SeedResidues";
	private static final String CHAIN_ATTR = "Chain";
	// static final String BACKBONE_ATTR = "BackboneInteraction";
	// static final String SIDECHAIN_ATTR = "SideChainInteraction";

	private StructureManager structureManager;
	private ChimeraManager chimeraManager;

	public CreateStructureNetworkTask(StructureManager structureManager) {
		this.structureManager = structureManager;
		this.chimeraManager = structureManager.getChimeraManager();

		networkName = getRINName();
		includeInteracions = new ListSingleSelection<String>(interactionArray);
		includeInteracions.setSelectedValue(interactionArray[0]);
		addHydrogens = false;
		ignoreWater = true;
		createCombiEdges = false;
		includeContacts = true;
		overlapCutoffCont = -0.4;
		hbondAllowanceCont = 0.0;
		bondSepCont = 4;
		includeClashes = false;
		overlapCutoffClash = 0.6;
		hbondAllowanceClash = 0.4;
		bondSepClash = 4;
		includeHBonds = false;
		removeRedContacts = true;
		relaxHBonds = false;
		distSlop = 0.4;
		angleSlop = 20;
		includeConnectivity = false;
		includeDistance = false;
		distCutoff = 5.0;
	}

	@ProvidesTitle
	public String getTitle() {
		return "Residue Interaction Network Options";
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("Create residue interaction network");
		taskMonitor.setStatusMessage("Adding nodes ...");
		// Save selected nodes indexed by their name
		Map<String, CyNode> nodeMap = new HashMap<String, CyNode>();
		chimeraManager.stopListening();
		CyNetwork rin = createNetwork();
		List<String> residues = chimeraManager.getSelectedResidueSpecs();
		for (String res : residues) {
			// System.out.println("get selected residue");
			createResidueNode(rin, nodeMap, res);
		}

		// add hydrogens first
		if (addHydrogens) {
			System.out.println("Adding hydrogens");
			taskMonitor.setStatusMessage("Adding hydrogens ...");
			chimeraManager.sendChimeraCommand("addh hbond true", false);
		}
		if (includeContacts) {
			System.out.println("Getting contacts");
			taskMonitor.setStatusMessage("Getting contacts ...");
			chimeraManager.stopListening();
			List<String> replyList = chimeraManager.sendChimeraCommand(
					getContactCommand(overlapCutoffCont, hbondAllowanceCont, bondSepCont), true);
			if (replyList != null) {
				parseContactReplies(replyList, rin, nodeMap, CONTACTEDGE);
			}
		}
		if (includeClashes) {
			System.out.println("Getting clashes");
			taskMonitor.setStatusMessage("Getting clashes ...");
			chimeraManager.stopListening();
			List<String> replyList = chimeraManager.sendChimeraCommand(
					getContactCommand(overlapCutoffClash, hbondAllowanceClash, bondSepClash), true);
			if (replyList != null) {
				parseContactReplies(replyList, rin, nodeMap, CLASHEDGE);
			}
		}
		if (includeHBonds) {
			System.out.println("Getting Hydrogen Bonds");
			taskMonitor.setStatusMessage("Getting hydrogen bonds ...");
			chimeraManager.stopListening();
			List<String> replyList = chimeraManager.sendChimeraCommand(getHBondCommand(), true);
			if (replyList != null) {
				parseHBondReplies(replyList, rin, nodeMap);
			}
		}
		if (includeConnectivity) {
			taskMonitor.setStatusMessage("Getting connectivity ...");
			chimeraManager.stopListening();
			List<String> replyList = chimeraManager.sendChimeraCommand("list physicalchains", true);
			if (replyList != null) {
				parseConnectivityReplies(replyList, rin);
			}
		}
		if (includeDistance) {
			System.out.println("Getting distances ...");
			taskMonitor.setStatusMessage("Getting distances ...");
			List<String> replyList = chimeraManager.sendChimeraCommand(getDistanceCommand(), true);
			if (replyList != null) {
				parseDistanceReplies(replyList, rin, nodeMap);
			}
		}
		if (createCombiEdges) {
			// add "combi" edges
			taskMonitor.setStatusMessage("Creating combined edges ...");
			addCombinedEdges(rin);
		}

		taskMonitor.setStatusMessage("Finalizing ...");

		// register network
		CyNetworkManager cyNetworkManager = (CyNetworkManager) structureManager
				.getService(CyNetworkManager.class);
		cyNetworkManager.addNetwork(rin);

		// structureManager.ignoreCySelection = false;
		// Activate structureViz for all of our nodes
		structureManager.addStructureNetwork(rin);
		finalizeNetwork(rin);
		chimeraManager.startListening();
	}

	private String getContactCommand(double overlapCutoff, double hbondAllowance, int bondSep) {
		String atomspec1 = "";
		String atomspec2 = "";
		// "Within selection"
		if (includeInteracions.getSelectedValue() == interactionArray[0]) {
			// among the specified atoms
			atomspec1 = "sel";
			atomspec2 = "test self";
		}
		// "Between selection and all other atoms"
		else if (includeInteracions.getSelectedValue() == interactionArray[1]) {
			// between the specified atoms and all other atoms
			atomspec1 = "sel";
			atomspec2 = "test other";
		}
		// "All of the above"
		else if (includeInteracions.getSelectedValue() == interactionArray[2]) {
			// intra-model interactions between the specified atoms and all
			// other atoms
			atomspec1 = "sel";
			atomspec2 = "test model";
		}
		// "Between models"
		// else if (includeInteracions.getSelectedValue() ==
		// interactionArray[3]) {
		// // between the specified atoms and all other atoms
		// atomspec1 = "#" + chimeraManager.getChimeraModel().getModelNumber();
		// atomspec2 = "test other";
		// }
		// // Between and within all models
		// else {
		// atomspec1 = "#*";
		// atomspec2 = "test self";
		// }
		// Create the command
		String command = "findclash " + atomspec1
				+ " makePseudobonds false log true namingStyle command overlapCutoff "
				+ overlapCutoff + " hbondAllowance " + hbondAllowance + " bondSeparation "
				+ bondSep + " " + atomspec2;
		return command;
	}

	private String getHBondCommand() {
		// for which atoms to find hydrogen bonds
		String atomspec = "";
		// intermodel: whether to look for H-bonds between models
		// intramodel: whether to look for H-bonds within models.
		String modelrestr = "";
		// "Within selection"
		if (includeInteracions.getSelectedValue() == interactionArray[0]) {
			// Limit H-bond detection to H-bonds with both atoms selected
			atomspec = "selRestrict both";
			modelrestr = "intramodel true intermodel true";
		}
		// "Between selection and all atoms"
		else if (includeInteracions.getSelectedValue() == interactionArray[1]) {
			// Limit H-bond detection to H-bonds with at least one atom selected
			atomspec = "selRestrict any";
			modelrestr = "intramodel false intermodel true";
		}
		// "Within selection and all atoms"
		else if (includeInteracions.getSelectedValue() == interactionArray[2]) {
			// Limit H-bond detection to H-bonds with at least one atom selected
			atomspec = "selRestrict any";
			modelrestr = "intramodel true intermodel true";
		}
		// "Between models"
		// else if (includeInteracions.getSelectedValue() ==
		// interactionArray[3]) {
		// // Restrict H-bond detection to the specified model
		// atomspec = "spec #*";
		// modelrestr = "intramodel false intermodel true";
		// }
		// // Between and within models
		// else {
		// atomspec = "spec #*";
		// modelrestr = "intramodel true intermodel true";
		// }
		String command = "findhbond " + atomspec + " " + modelrestr
				+ " makePseudobonds false log true namingStyle command";
		if (relaxHBonds) {
			command += " relax true distSlop " + distSlop + " angleSlop " + angleSlop;
		}
		return command;
	}

	private String getDistanceCommand() {
		String atomspec = "";
		// "Within selection"
		if (includeInteracions.getSelectedValue() == interactionArray[0]) {
			// among the specified atoms
			atomspec = "@CA&sel";
		}
		// "Between selection and all other atoms"
		else if (includeInteracions.getSelectedValue() == interactionArray[1]) {
			// between the specified atoms and all other atoms
			// TODO: How to get the distances between selection and other atoms
			atomspec = "@CA&sel|@CA";
		}
		// "All of the above"
		else if (includeInteracions.getSelectedValue() == interactionArray[2]) {
			// intra-model interactions between the specified atoms and all
			// other atoms
			// TODO: How to get the distances within selection and other atoms
			atomspec = "@CA";
		}
		// Create the command
		String command = "list distmat " + atomspec;
		return command;
	}

	/**
	 * Clash replies look like: *preamble* *header line* *clash lines* where preamble is: Allowed
	 * overlap: -0.4 H-bond overlap reduction: 0 Ignore contacts between atoms separated by 4 bonds
	 * or less Ignore intra-residue contacts 44 contacts and the header line is: atom1 atom2 overlap
	 * distance and the clash lines look like: :2470.A@N :323.A@OD2 -0.394 3.454
	 */
	private List<CyEdge> parseContactReplies(List<String> replyLog, CyNetwork rin,
			Map<String, CyNode> nodeMap, String edgeType) {
		// Scan for our header line
		boolean foundHeader = false;
		int index = 0;
		for (index = 0; index < replyLog.size(); index++) {
			String str = replyLog.get(index);

			if (str.trim().startsWith("atom1")) {
				foundHeader = true;
				break;
			}
		}
		if (!foundHeader)
			return null;

		Map<CyEdge, Double> distanceMap = new HashMap<CyEdge, Double>();
		Map<CyEdge, Double> overlapMap = new HashMap<CyEdge, Double>();
		for (++index; index < replyLog.size(); index++) {
			// System.out.println(replyLog.get(index));
			String[] line = replyLog.get(index).trim().split("\\s+");
			if (line.length != 4)
				continue;

			CyEdge edge = createEdge(rin, nodeMap, line[0], line[1], edgeType);
			if (edge == null) {
				continue;
			}

			// We want the smallest distance
			updateMap(distanceMap, edge, line[3], -1);
			// We want the largest overlap
			updateMap(overlapMap, edge, line[2], 1);
		}

		// OK, now update the edge attributes we want
		for (CyEdge edge : distanceMap.keySet()) {
			rin.getRow(edge).set(DISTANCE_ATTR, distanceMap.get(edge));
			rin.getRow(edge).set(OVERLAP_ATTR, overlapMap.get(edge));
		}

		return new ArrayList<CyEdge>(distanceMap.keySet());
	}

	// H-bonds (donor, acceptor, hydrogen, D..A dist, D-H..A dist):
	/**
	 * Finding acceptors in model '1tkk' Building search tree of acceptor atoms Finding donors in
	 * model '1tkk' Matching donors in model '1tkk' to acceptors Finding intermodel H-bonds Finding
	 * intramodel H-bonds Constraints relaxed by 0.4 angstroms and 20 degrees Models used: #0 1tkk
	 * H-bonds (donor, acceptor, hydrogen, D..A dist, D-H..A dist): ARG 24.A NH1 GLU 2471.A OE1 no
	 * hydrogen 3.536 N/A LYS 160.A NZ GLU 2471.A O no hydrogen 2.680 N/A LYS 162.A NZ ALA 2470.A O
	 * no hydrogen 3.022 N/A LYS 268.A NZ GLU 2471.A O no hydrogen 3.550 N/A ILE 298.A N GLU 2471.A
	 * OE2 no hydrogen 3.141 N/A ALA 2470.A N THR 135.A OG1 no hydrogen 2.814 N/A ALA 2470.A N ASP
	 * 321.A OD1 no hydrogen 2.860 N/A ALA 2470.A N ASP 321.A OD2 no hydrogen 3.091 N/A ALA 2470.A N
	 * ASP 323.A OD1 no hydrogen 2.596 N/A ALA 2470.A N ASP 323.A OD2 no hydrogen 3.454 N/A GLU
	 * 2471.A N SER 296.A O no hydrogen 2.698 N/A HOH 2541.A O GLU 2471.A OE1 no hydrogen 2.746 N/A
	 * HOH 2577.A O GLU 2471.A O no hydrogen 2.989 N/A
	 */
	private List<CyEdge> parseHBondReplies(List<String> replyLog, CyNetwork rin,
			Map<String, CyNode> nodeMap) {
		// Scan for our header line
		boolean foundHeader = false;
		int index = 0;
		for (index = 0; index < replyLog.size(); index++) {
			String str = replyLog.get(index);
			if (str.trim().startsWith("H-bonds")) {
				foundHeader = true;
				break;
			}
		}
		if (!foundHeader) {
			return null;
		}

		Map<CyEdge, Double> distanceMap = new HashMap<CyEdge, Double>();
		for (++index; index < replyLog.size(); index++) {
			// System.out.println(replyLog.get(index));
			String[] line = replyLog.get(index).trim().split("\\s+");
			if (line.length != 5 && line.length != 6)
				continue;

			CyEdge edge = createEdge(rin, nodeMap, line[0], line[1], HBONDEDGE);
			if (edge == null) {
				continue;
			}
			// System.out.println(rin.getRow(edge).get(CyNetwork.NAME,
			// String.class));
			String distance = line[3];
			if ((line[2].equals("no") && line[3].equals("hydrogen")) || addHydrogens) {
				distance = line[4];
			}
			updateMap(distanceMap, edge, distance, -1); // We want the smallest
														// distance
		}

		// OK, now update the edge attributes we want
		for (CyEdge edge : distanceMap.keySet()) {
			rin.getRow(edge).set(DISTANCE_ATTR, distanceMap.get(edge));
		}

		return new ArrayList<CyEdge>(distanceMap.keySet());
	}

	/**
	 * Parse the connectivity information from Chimera. The data is of the form: physical chain
	 * #0:283.A #0:710.A physical chain #0:283.B #0:710.B physical chain #0:283.C #0:710.C
	 * 
	 * We don't use this data to create new nodes -- only new edges. If two nodes are within the
	 * same physical chain, we connect them with a "Connected" edge
	 */
	private List<CyEdge> parseConnectivityReplies(List<String> replyLog, CyNetwork rin) {
		List<CyEdge> edgeList = new ArrayList<CyEdge>();
		List<ChimeraResidue[]> rangeList = new ArrayList<ChimeraResidue[]>();
		for (String line : replyLog) {
			String[] tokens = line.split(" ");
			if (tokens.length != 4)
				continue;
			String start = tokens[2];
			String end = tokens[3];

			ChimeraResidue[] range = new ChimeraResidue[2];

			// Get the residues from the reside spec
			range[0] = ChimUtils.getResidue(start, chimeraManager);
			range[1] = ChimUtils.getResidue(end, chimeraManager);
			if (range[0] != null && range[1] != null) {
				rangeList.add(range);
			}
		}

		// For each node pair, figure out if the pair is connected
		List<CyNode> nodes = rin.getNodeList();
		for (int i = 0; i < nodes.size(); i++) {
			CyNode node1 = nodes.get(i);
			// System.out.println("Getting the range for the first node..."+node1);
			ChimeraResidue[] range = getRange(rangeList, node1, rin);
			if (range == null)
				continue;
			for (int j = i + 1; j < nodes.size(); j++) {
				CyNode node2 = nodes.get(j);
				// System.out.println("Seeing if node2 "+node2+" is in the range...");
				if (inRange2(range, node1, node2, rin)) {
					// System.out.println("....it is");
					// These two nodes are connected
					edgeList.add(createConnectivityEdge(rin, node1, node2));
				}
			}
		}

		// Now, make the edges based on whether any pair of nodes are in the
		// same range
		return edgeList;
	}

	/**
	 * 
	 * distmat #0:36.A@CA #0:37.A@CA 3.777 distmat #0:36.A@CA #0:38.A@CA 6.663
	 * 
	 * @param replyLog
	 * @param rin
	 * @param nodeMap
	 * @return
	 */
	private List<CyEdge> parseDistanceReplies(List<String> replyLog, CyNetwork rin,
			Map<String, CyNode> nodeMap) {
		List<CyEdge> distEdges = new ArrayList<CyEdge>();
		for (int index = 0; index < replyLog.size(); index++) {
			// System.out.println(replyLog.get(index));
			String[] line = replyLog.get(index).trim().split("\\s+");
			if (line.length != 4)
				continue;

			String distance = line[3];
			// try to read distance and create an edge if distance between atoms smaller than cutoff
			// special case of cutoff = 0: create all edges
			try {
				Double distNum = Double.parseDouble(distance);
				if (distCutoff == 0.0 || distNum <= distCutoff) {
					CyEdge edge = createEdge(rin, nodeMap, line[1], line[2], DISTEDGE);
					if (edge == null) {
						continue;
					}
					rin.getRow(edge).set(DISTANCE_ATTR, distNum);
				}
			} catch (Exception ex) {
				// ignore
			}
		}
		return distEdges;
	}

	private CyEdge createEdge(CyNetwork rin, Map<String, CyNode> nodeMap, String sourceAlias,
			String targetAlias, String type) {
		// Create our two nodes. Note that makeResidueNode also adds three
		// attributes:
		// 1) FunctionalResidues; 2) Seed; 3) SideChainOnly
		CyNode source = createResidueNode(rin, nodeMap, sourceAlias);
		CyNode target = createResidueNode(rin, nodeMap, targetAlias);
		if (source == null || target == null) {
			return null;
		}
		String interactingAtoms = sourceAlias + "," + targetAlias;
		String sourceAtom = ChimUtils.getAtomName(sourceAlias);
		String targetAtom = ChimUtils.getAtomName(targetAlias);
		String interactionSubtype = type
				+ " "
				+ ChimUtils.getIntSubtype(rin.getRow(source).get(CyNetwork.NAME, String.class),
						sourceAtom)
				+ "_"
				+ ChimUtils.getIntSubtype(rin.getRow(target).get(CyNetwork.NAME, String.class),
						targetAtom);

		// Create our edge
		CyEdge edge = null;
		if (removeRedContacts && type.equals("hbond")) {
			List<CyEdge> existingEdges = rin.getConnectingEdgeList(source, target, Type.ANY);
			if (existingEdges.size() > 0) {
				for (CyEdge exEdge : existingEdges) {
					if (rin.getRow(exEdge).get(CyEdge.INTERACTION, String.class).equals("contact")
							&& rin.getRow(exEdge).get(INTATOMS_ATTR, String.class)
									.equals(interactingAtoms)) {
						edge = exEdge;
						rin.getRow(edge).set(OVERLAP_ATTR, null);
						break;
					}
				}
			}
		}
		if (edge == null) {
			edge = rin.addEdge(source, target, true);
		}
		String edgeName = rin.getRow(source).get(CyNetwork.NAME, String.class) + " (" + type + ") "
				+ rin.getRow(target).get(CyNetwork.NAME, String.class);
		rin.getRow(edge).set(CyNetwork.NAME, edgeName);
		rin.getRow(edge).set(CyEdge.INTERACTION, type);
		rin.getRow(edge).set(INTATOMS_ATTR, interactingAtoms);
		rin.getRow(edge).set(INTSUBTYPE_ATTR, interactionSubtype);
		return edge;
	}

	private CyEdge createConnectivityEdge(CyNetwork rin, CyNode node1, CyNode node2) {
		CyEdge edge = rin.addEdge(node1, node2, true);
		String edgeName = rin.getRow(node1).get(CyNetwork.NAME, String.class) + " (backbone)"
				+ rin.getRow(node2).get(CyNetwork.NAME, String.class);
		rin.getRow(edge).set(CyNetwork.NAME, edgeName);
		rin.getRow(edge).set(CyEdge.INTERACTION, "backbone");
		rin.getRow(edge).set(INTSUBTYPE_ATTR, "backbone");
		return edge;
	}

	private CyNode createResidueNode(CyNetwork rin, Map<String, CyNode> nodeMap, String alias) {
		// alias is a atomSpec of the form [#model]:residueNumber@atom
		// We want to convert that to a node identifier of [pdbid#]ABC nnn
		// and add FunctionalResidues and BackboneOnly attributes
		// boolean singleModel = false;
		ChimeraModel model = ChimUtils.getModel(alias, chimeraManager);
		if (model == null) {
			model = chimeraManager.getChimeraModel();
			// singleModel = true;
		}
		ChimeraResidue residue = ChimUtils.getResidue(alias, model);
		System.out.println(residue.isSelected());
		if (ignoreWater && residue.getType().equals("HOH")) {
			return null;
		}
		// boolean backbone = ChimUtils.isBackbone(alias);

		int displayType = ChimeraResidue.getDisplayType();
		ChimeraResidue.setDisplayType(ChimeraResidue.THREE_LETTER);
		// OK, now we have everything we need, create the node
		String nodeName = residue.toString().trim() + "." + residue.getChainId();
		ChimeraResidue.setDisplayType(displayType);

		// if (!singleModel)
		nodeName = model.getModelName() + "#" + nodeName;

		// Create the node if it does not already exist in the network
		CyNode node = null;
		if (!nodeMap.containsKey(nodeName)) {
			node = rin.addNode();
			rin.getRow(node).set(CyNetwork.NAME, nodeName);
			nodeMap.put(nodeName, node);

			// Add attributes from Chimera
			rin.getRow(node).set(ChimUtils.RESIDUE_ATTR,
					model.getModelName() + "#" + residue.getIndex() + "." + residue.getChainId());
			rin.getRow(node).set(
					ChimUtils.RINALYZER_ATTR,
					model.getModelName() + ":" + residue.getChainId() + ":" + residue.getIndex()
							+ ":_:" + residue.getType());
			rin.getRow(node).set(SEED_ATTR, Boolean.valueOf(residue.isSelected()));
			rin.getRow(node).set(CHAIN_ATTR, residue.getChainId());
			// if (backbone)
			// rin.getRow(node).set(BACKBONE_ATTR, Boolean.TRUE);
			// else
			// rin.getRow(node).set(SIDECHAIN_ATTR, Boolean.TRUE);

			// Add structureViz attributes
			String smiles = ChimUtils.toSMILES(residue.getType());
			if (smiles != null) {
				rin.getRow(node).set(SMILES_ATTR, smiles);
			}
			rin.getRow(node).set(STRUCTURE_ATTR, model.getModelName());
		} else {
			node = nodeMap.get(nodeName);
		}
		return node;
	}

	private void updateMap(Map<CyEdge, Double> map, CyEdge edge, String value, int comparison) {
		// Save the minimum distance between atoms
		Double v = Double.valueOf(value);
		if (map.containsKey(edge)) {
			if (comparison < 0 && map.get(edge).compareTo(v) > 0)
				map.put(edge, v);
			else if (comparison > 0 && map.get(edge).compareTo(v) < 0)
				map.put(edge, v);
		} else {
			map.put(edge, v);
		}
	}

	private ChimeraResidue[] getRange(List<ChimeraResidue[]> rangeList, CyNode node, CyNetwork rin) {
		for (ChimeraResidue[] range : rangeList) {
			if (inRange(range, node, rin))
				return range;
		}
		return null;
	}

	private boolean inRange(ChimeraResidue[] range, CyNode node, CyNetwork rin) {
		String residueAttr = rin.getRow(node).get(ChimUtils.RESIDUE_ATTR, String.class);
		ChimeraStructuralObject cso = ChimUtils.fromAttribute(residueAttr, chimeraManager);
		// Models can't be in a range...
		if (cso == null || cso instanceof ChimeraModel)
			return false;

		// A chain might be in a range -- check this
		if (cso instanceof ChimeraChain) {
			String chainID = ((ChimeraChain) cso).getChainId();
			return inChainRange(range, chainID);
		}

		// OK, we have a residue, but we need to be careful to make
		// sure that the chains match
		ChimeraResidue residue = (ChimeraResidue) cso;
		if (inChainRange(range, residue.getChainId())) {
			return true;
		}

		int startIndex = Integer.parseInt(range[0].getIndex());
		int endIndex = Integer.parseInt(range[1].getIndex());
		int residueIndex = Integer.parseInt(residue.getIndex());

		if (endIndex < startIndex) {
			if (endIndex <= residueIndex && residueIndex <= startIndex)
				return true;
		} else {
			if (startIndex <= residueIndex && residueIndex <= endIndex)
				return true;
		}

		return false;
	}

	private boolean inRange2(ChimeraResidue[] range, CyNode node1, CyNode node2, CyNetwork rin) {
		ChimeraStructuralObject cso1 = ChimUtils.fromAttribute(
				rin.getRow(node1).get(ChimUtils.RESIDUE_ATTR, String.class), chimeraManager);
		ChimeraStructuralObject cso2 = ChimUtils.fromAttribute(
				rin.getRow(node2).get(ChimUtils.RESIDUE_ATTR, String.class), chimeraManager);
		// Models can't be in a range...
		if (cso1 == null || cso1 instanceof ChimeraModel || cso1 instanceof ChimeraChain
				|| cso2 == null || cso2 instanceof ChimeraModel || cso2 instanceof ChimeraChain)
			return false;

		// OK, we have a residue, but we need to be careful to make
		// sure that the chains match
		ChimeraResidue residue1 = (ChimeraResidue) cso1;
		ChimeraResidue residue2 = (ChimeraResidue) cso2;

		int startIndex = Integer.parseInt(range[0].getIndex());
		int endIndex = Integer.parseInt(range[1].getIndex());
		int residueIndex1 = Integer.parseInt(residue1.getIndex());
		int residueIndex2 = Integer.parseInt(residue2.getIndex());
		int diff = Math.abs(residueIndex1 - residueIndex2);

		if (endIndex < startIndex) {
			if (diff == 1 && endIndex <= residueIndex1 && residueIndex1 <= startIndex
					&& endIndex <= residueIndex2 && residueIndex2 <= startIndex)
				return true;
		} else {
			if (diff == 1 && startIndex <= residueIndex1 && residueIndex1 <= endIndex
					&& startIndex <= residueIndex2 && residueIndex2 <= endIndex)
				return true;
		}
		return false;
	}

	private boolean inChainRange(ChimeraResidue[] range, String chainID) {
		String start = range[0].getChainId();
		String end = range[1].getChainId();

		if (start.equals(end))
			return false;

		if (start.compareTo(end) > 0) {
			end = range[0].getChainId();
			start = range[1].getChainId();
		}

		if (start.compareTo(chainID) <= 0 && chainID.compareTo(end) <= 0)
			return true;

		return false;
	}

	private void addCombinedEdges(CyNetwork rin) {

	}

	private CyNetwork createNetwork() {
		// get factories, etc.
		CyNetworkFactory cyNetworkFactory = (CyNetworkFactory) structureManager
				.getService(CyNetworkFactory.class);

		// Create the network
		CyNetwork rin = cyNetworkFactory.createNetwork();
		rin.getRow(rin).set(CyNetwork.NAME, networkName);

		// Create new attributes
		rin.getDefaultNodeTable().createColumn(ChimUtils.RESIDUE_ATTR, String.class, false);
		rin.getDefaultNodeTable().createColumn(ChimUtils.RINALYZER_ATTR, String.class, false);
		rin.getDefaultEdgeTable().createColumn(DISTANCE_ATTR, Double.class, false);
		rin.getDefaultEdgeTable().createColumn(OVERLAP_ATTR, Double.class, false);
		rin.getDefaultEdgeTable().createColumn(INTSUBTYPE_ATTR, String.class, false);
		rin.getDefaultEdgeTable().createColumn(INTATOMS_ATTR, String.class, false);
		rin.getDefaultNodeTable().createColumn(SMILES_ATTR, String.class, false);
		rin.getDefaultNodeTable().createColumn(STRUCTURE_ATTR, String.class, false);
		rin.getDefaultNodeTable().createColumn(SEED_ATTR, Boolean.class, false);
		rin.getDefaultNodeTable().createColumn(CHAIN_ATTR, String.class, false);

		// return network
		return rin;
	}

	private void finalizeNetwork(CyNetwork network) {
		// get factories, etc.
		CyNetworkViewFactory cyNetworkViewFactory = (CyNetworkViewFactory) structureManager
				.getService(CyNetworkViewFactory.class);
		CyNetworkViewManager cyNetworkViewManager = (CyNetworkViewManager) structureManager
				.getService(CyNetworkViewManager.class);

		// remove single nodes
		// List<CyNode> singleNodes = new ArrayList<CyNode>();
		// for (CyNode node : network.getNodeList()) {
		// if (network.getAdjacentEdgeList(node, Type.ANY).size() == 0) {
		// singleNodes.add(node);
		// }
		// }
		// network.removeNodes(singleNodes);

		// Create a network view
		CyNetworkView rinView = cyNetworkViewFactory.createNetworkView(network);
		cyNetworkViewManager.addNetworkView(rinView);
		// Do a layout
		// CyLayoutAlgorithmManager cyLayoutManager = (CyLayoutAlgorithmManager)
		// structureViz
		// .getService(CyLayoutAlgorithmManager.class);
		// CyLayoutAlgorithm layout = cyLayoutManager.getDefaultLayout();
		// insertTasksAfterCurrentTask(layout.createTaskIterator(rinView,
		// layout.getDefaultLayoutContext(), layout.ALL_NODE_VIEWS, null));
		ApplyPreferredLayoutTaskFactory layoutTaskFactory = (ApplyPreferredLayoutTaskFactory) structureManager
				.getService(ApplyPreferredLayoutTaskFactory.class);
		Set<CyNetworkView> views = new HashSet<CyNetworkView>();
		views.add(rinView);
		insertTasksAfterCurrentTask(layoutTaskFactory.createTaskIterator(views));

		// annotate
		NetworkTaskFactory annotateFactory = new AnnotateStructureNetworkTaskFactory(
				structureManager);
		if (annotateFactory != null) {
			TunableSetter tunableSetter = (TunableSetter) structureManager
					.getService(TunableSetter.class);
			Map<String, Object> tunables = new HashMap<String, Object>();
			List<String> resAttr = structureManager.getAllResidueAttributes();
			ListMultipleSelection<String> resAttrTun = new ListMultipleSelection<String>(resAttr);
			resAttrTun.setSelectedValues(resAttr);
			tunables.put("residueAttributes", resAttrTun);
			TaskManager<?, ?> tm = (TaskManager<?, ?>) structureManager
					.getService(TaskManager.class);
			insertTasksAfterCurrentTask(tunableSetter.createTaskIterator(
					annotateFactory.createTaskIterator(network), tunables));
		}
		// Set vizmap
		NetworkViewTaskFactory rinalyzerVisProps = (NetworkViewTaskFactory) structureManager
				.getService(NetworkViewTaskFactory.class,
						"(&(commandNamespace=rinalyzer)(command=initRinVisProps))");
		if (rinalyzerVisProps != null) {
			insertTasksAfterCurrentTask(rinalyzerVisProps.createTaskIterator(rinView));
		} else {
			VisualMappingManager cyVmManager = (VisualMappingManager) structureManager
					.getService(VisualMappingManager.class);
			VisualStyleFactory cyVsFactory = (VisualStyleFactory) structureManager
					.getService(VisualStyleFactory.class);
			VisualStyle rinStyle = null;
			for (VisualStyle vs : cyVmManager.getAllVisualStyles()) {
				if (vs.getTitle().equals("RIN style")) {
					rinStyle = vs;
				}
			}
			if (rinStyle == null) {
				rinStyle = cyVsFactory.createVisualStyle(cyVmManager.getDefaultVisualStyle());
				rinStyle.setTitle("RIN style");
				cyVmManager.addVisualStyle(rinStyle);
			}
			cyVmManager.setVisualStyle(rinStyle, rinView);
			rinStyle.apply(rinView);
		}
		rinView.updateView();
	}

	private String getRINName() {
		String name = "RIN ";
		Map<Integer, ChimeraModel> models = chimeraManager.getSelectedModels();
		for (ChimeraModel model : models.values()) {
			name += model.getModelName() + " ";
		}
		return name;
	}

}
