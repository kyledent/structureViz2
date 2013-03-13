package edu.ucsf.rbvi.structureViz2.internal.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableUtil;

/**
 * This object maintains the relationship between Chimera objects and Cytoscape
 * objects.
 */
public class StructureManager {
	static final String[] defaultStructureKeys = { "Structure", "pdb", "pdbFileName",
			"PDB ID", "structure", "biopax.xref.PDB", "pdb_ids" };
	static final String[] defaultChemStructKeys = { "Smiles", "smiles", "SMILES" };
	static final String[] defaultResidueKeys = { "FunctionalResidues", "ResidueList" };

	Map<CyNetwork,StructureSettings> settings = null;
	// So far just a list, may become a map of CyIdentifiable <-> Structure?
	List<Structure> currentStructures = null;
	
	public StructureManager() {
		settings = new HashMap<CyNetwork, StructureSettings>();
		currentStructures = new ArrayList<Structure>();
	}

	public void setStructureSettings(CyNetwork network, StructureSettings newSettings) {
		settings.put(network,newSettings);
	}

	// TODO: this is just for testing -- eventually we need
	// to return the actual list of keys for this network
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
		return Arrays.asList(defaultChemStructKeys);
	}

	public List<String> getAllResidueKeys(CyNetwork network) {
		return Arrays.asList(defaultResidueKeys);
	}

	public List<String> getCurrentResidueKeys(CyNetwork network) {
		return Arrays.asList(defaultResidueKeys);
	}

	public boolean hasNodeStructures(CyNetwork network, Collection<CyNode> nodeSet) {
		if (network == null)
			return false;
		CyTable nodeTable = network.getDefaultNodeTable();
		List<String> attrsFound = getMatchingAttributes(nodeTable, getStructureAttributes(network));
		Collection idSet = nodeSet;
		return hasStructures(idSet, nodeTable, attrsFound);
	}

	public Map<CyIdentifiable, List<String>> getNodeStructures(CyNetwork network, Collection<CyNode> nodeSet) {
		if (network == null)
			return null;
		CyTable nodeTable = network.getDefaultNodeTable();
		List<String> attrsFound = getMatchingAttributes(nodeTable, getStructureAttributes(network));
		Collection idSet = nodeSet;
		return getStructures(idSet, nodeTable, attrsFound);
	}

	public Structure getStructure(CyNetwork network, CyIdentifiable obj) {
		for (Structure structure : currentStructures) {
			if (network.equals(structure.getNetwork()) && obj.equals(structure.getCyIdentifiable())) {
				return structure;
			}
		}
		return null;
	}
	
	public boolean addStructure(Structure newStructure) {
		if (currentStructures.contains(newStructure)) {
			return false;
		}
		return currentStructures.add(newStructure);
	}

	public void removeStructure(Structure oldStructure) {
		currentStructures.remove(oldStructure);
	}

	public boolean hasEdgeStructures(CyNetwork network, Collection<CyEdge> edgeSet) {
		if (network == null)
			return false;
		CyTable edgeTable = network.getDefaultEdgeTable();
		List<String> attrsFound = getMatchingAttributes(edgeTable, getStructureAttributes(network));
		Collection idSet = edgeSet;
		return hasStructures(idSet, edgeTable, attrsFound);
	}

	private List<String> getStructureAttributes(CyNetwork network) {
		if (settings.containsKey(network))
			return settings.get(network).getStructureColumns().getSelectedValues();
		return Arrays.asList(defaultStructureKeys);
	}

	private boolean hasStructures(Collection<CyIdentifiable> objs, CyTable table,
			List<String> columns) {
		if (columns == null || columns.size() == 0)
			return false;
		if (objs == null)
			return true;

		for (CyIdentifiable obj : objs) {
			if (table.rowExists(obj.getSUID())) {
				CyRow row = table.getRow(obj.getSUID());
				for (String column : columns) {
					if (row.getRaw(column) != null) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// TODO: duplicated code with hasStructures, do we need both?
	private Map<CyIdentifiable, List<String>> getStructures(Collection<CyIdentifiable> objs, CyTable table,
			List<String> columns) {
		if (columns == null || columns.size() == 0 || objs == null)
			return null;
		Map<CyIdentifiable, List<String>> structures = new HashMap<CyIdentifiable, List<String>>();
		for (CyIdentifiable obj : objs) {
			if (table.rowExists(obj.getSUID())) {
				CyRow row = table.getRow(obj.getSUID());
				for (String column : columns) {
					// TODO: consider attributes that contain lists?
					String cell = row.get(column, String.class, "").trim();
					if (!cell.equals("")) {
						if (!structures.containsKey(obj)) {
							structures.put(obj, new ArrayList<String>());
						}
						structures.get(obj).add(cell);
					}
				}
			}
		}
		return structures;
	}

	private List<String> getMatchingAttributes(CyTable table, List<String> columns) {
		Set<String> columnNames = CyTableUtil.getColumnNames(table);

		List<String> columnsFound = new ArrayList<String>();
		for (String attribute : columns) {
			if (columnNames.contains(attribute))
				columnsFound.add(attribute);
		}

		return columnsFound;
	}

}