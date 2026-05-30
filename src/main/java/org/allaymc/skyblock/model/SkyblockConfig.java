package org.allaymc.skyblock.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model holding block values and starter items.
 * Full implementation in task 2.6.
 */
public class SkyblockConfig {

    private Map<String, Integer> blockValues = new HashMap<>();
    private List<StarterItem> starterItems;

    /**
     * Returns the configured point value for a block type identifier.
     * Returns 0 for unknown block types.
     */
    public int getBlockValue(String blockTypeId) {
        return blockValues.getOrDefault(blockTypeId, 0);
    }

    public Map<String, Integer> getBlockValues() {
        return blockValues;
    }

    public void setBlockValues(Map<String, Integer> blockValues) {
        this.blockValues = blockValues;
    }

    public List<StarterItem> getStarterItems() {
        return starterItems;
    }

    public void setStarterItems(List<StarterItem> starterItems) {
        this.starterItems = starterItems;
    }

    /**
     * Represents an item placed in the starter chest.
     */
    public static class StarterItem {
        private String itemTypeId;
        private int count;

        public StarterItem() {}

        public String getItemTypeId() {
            return itemTypeId;
        }

        public void setItemTypeId(String itemTypeId) {
            this.itemTypeId = itemTypeId;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
