package fr.themode.demo.generator;

import lombok.Data;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.BlockPosition;

import java.util.HashMap;
import java.util.Map;

@Data
public class Structure {

	private final Map<BlockPosition, Block> blocks = new HashMap<>();

	public void build(ChunkBatch batch, BlockPosition pos) {
		blocks.forEach((bPos, block) -> {
			batch.setBlock(bPos.clone().add(pos), block);
		});
	}

	public void addBlock(Block block, int localX, int localY, int localZ) {
		blocks.put(new BlockPosition(localX, localY, localZ), block);
	}

}
