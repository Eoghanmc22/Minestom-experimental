package net.minestom.server.instance;

import com.extollit.gaming.ai.path.model.ColumnarOcclusionFieldList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.data.Data;
import net.minestom.server.data.SerializableData;
import net.minestom.server.data.SerializableDataImpl;
import net.minestom.server.entity.pathfinding.PFBlockDescription;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.CustomBlock;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.utils.BlockPosition;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.binary.BinaryReader;
import net.minestom.server.utils.binary.BinaryWriter;
import net.minestom.server.utils.chunk.ChunkCallback;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.time.CooldownUtils;
import net.minestom.server.utils.time.UpdateOption;
import net.minestom.server.world.biomes.Biome;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DynamicChunk extends Chunk {

	private static final int DATA_FORMAT_VERSION = 2;

    // blocks id based on coordinate, see Chunk#getBlockIndex
    // WARNING: those arrays are NOT thread-safe
    // and modifying them can cause issue with block data, update, block entity and the cached chunk packet
    protected final short[] blocksStateId = new short[CHUNK_SIZE_X * CHUNK_SIZE_Y * CHUNK_SIZE_Z];
    protected final short[] customBlocksId = new short[CHUNK_SIZE_X * CHUNK_SIZE_Y * CHUNK_SIZE_Z];

	// Used to get all blocks with data (no null)
    // Key is still chunk coord
    protected Int2ObjectMap<Data> blocksData = new Int2ObjectOpenHashMap<>(16 * 16); // Start with the size of a single row

    // Contains CustomBlocks' block index which are updatable
    protected IntSet updatableBlocks = new IntOpenHashSet();
    // (block index)/(last update in ms)
    protected Int2LongMap updatableBlocksLastUpdate = new Int2LongOpenHashMap();

    // Block entities
    protected Set<Integer> blockEntities = new CopyOnWriteArraySet<>();

    public DynamicChunk(Instance instance, Biome[] biomes, int chunkX, int chunkZ, boolean generated) {
        super(instance, biomes, chunkX, chunkZ);
		setGenerated(generated);
    }

	@Override
    public void UNSAFE_setBlock(int x, int y, int z, short blockStateId, short customBlockId, Data data, boolean updatable) {

        {
            // Update pathfinder
            if (columnarSpace != null) {
                final ColumnarOcclusionFieldList columnarOcclusionFieldList = columnarSpace.occlusionFields();
                final PFBlockDescription blockDescription = PFBlockDescription.getBlockDescription(blockStateId);
                columnarOcclusionFieldList.onBlockChanged(x, y, z, blockDescription, 0);
            }
        }

        final int index = getBlockIndex(x, y, z);
        // True if the block is not complete air without any custom block capabilities
        final boolean hasBlock = blockStateId != 0 || customBlockId != 0;
        if (hasBlock) {
            this.blocksStateId[index] = blockStateId;
            this.customBlocksId[index] = customBlockId;
        } else {
            // Block has been deleted, clear cache and return

            this.blocksStateId[index] = 0; // Set to air
            this.customBlocksId[index] = 0; // Remove custom block

            this.blocksData.remove(index);

            this.updatableBlocks.remove(index);
            this.updatableBlocksLastUpdate.remove(index);

            this.blockEntities.remove(index);

            this.packetUpdated = false;
            return;
        }

        // Set the new data (or remove from the map if is null)
        if (data != null) {
            this.blocksData.put(index, data);
        } else {
            this.blocksData.remove(index);
        }

        // Set update consumer
        if (updatable) {
            this.updatableBlocks.add(index);
            this.updatableBlocksLastUpdate.put(index, System.currentTimeMillis());
        } else {
            this.updatableBlocks.remove(index);
            this.updatableBlocksLastUpdate.remove(index);
        }

        // Set block entity
        if (isBlockEntity(blockStateId)) {
            this.blockEntities.add(index);
        } else {
            this.blockEntities.remove(index);
        }

        this.packetUpdated = false;
    }

    @Override
    public void tick(long time, Instance instance) {
        if (updatableBlocks.isEmpty())
            return;

        // Block all chunk operation during the update
        final IntIterator iterator = new IntOpenHashSet(updatableBlocks).iterator();
        while (iterator.hasNext()) {
            final int index = iterator.nextInt();
            final CustomBlock customBlock = getCustomBlock(index);

            // Update cooldown
            final UpdateOption updateOption = customBlock.getUpdateOption();
            final long lastUpdate = updatableBlocksLastUpdate.get(index);
            final boolean hasCooldown = CooldownUtils.hasCooldown(time, lastUpdate, updateOption);
            if (hasCooldown)
                continue;

            this.updatableBlocksLastUpdate.put(index, time); // Refresh last update time

            final BlockPosition blockPosition = ChunkUtils.getBlockPosition(index, chunkX, chunkZ);
            final Data data = getBlockData(index);
            customBlock.update(instance, blockPosition, data);
        }
    }

    @Override
    public short getBlockStateId(int x, int y, int z) {
        final int index = getBlockIndex(x, y, z);
        if (!MathUtils.isBetween(index, 0, blocksStateId.length)) {
            return 0; // TODO: custom invalid block
        }
        return blocksStateId[index];
    }

    @Override
    public short getCustomBlockId(int x, int y, int z) {
        final int index = getBlockIndex(x, y, z);
        if (!MathUtils.isBetween(index, 0, blocksStateId.length)) {
            return 0; // TODO: custom invalid block
        }
        return customBlocksId[index];
    }

    @Override
    protected void refreshBlockValue(int x, int y, int z, short blockStateId, short customBlockId) {
        final int blockIndex = getBlockIndex(x, y, z);
        if (!MathUtils.isBetween(blockIndex, 0, blocksStateId.length)) {
            return;
        }

        this.blocksStateId[blockIndex] = blockStateId;
        this.customBlocksId[blockIndex] = customBlockId;
    }

    @Override
    protected void refreshBlockStateId(int x, int y, int z, short blockStateId) {
        final int blockIndex = getBlockIndex(x, y, z);
        if (!MathUtils.isBetween(blockIndex, 0, blocksStateId.length)) {
            return;
        }

        this.blocksStateId[blockIndex] = blockStateId;
    }

    @Override
    public Data getBlockData(int index) {
        return blocksData.get(index);
    }

    @Override
    public void setBlockData(int x, int y, int z, Data data) {
        final int index = getBlockIndex(x, y, z);
        if (data != null) {
            this.blocksData.put(index, data);
        } else {
            this.blocksData.remove(index);
        }
    }

    @Override
    public Set<Integer> getBlockEntities() {
        return blockEntities;
    }

    /**
     * Serialize this {@link Chunk} based on {@link #readChunk(BinaryReader, ChunkCallback)}
     * <p>
     * It is also used by the default {@link IChunkLoader} which is {@link MinestomBasicChunkLoader}
     *
     * @return the serialized chunk data
     */
    @Override
    public byte[] getSerializedData() {

        // Used for blocks data (unused if empty at the end)
        Object2ShortMap<String> typeToIndexMap = new Object2ShortOpenHashMap<>();

        BinaryWriter binaryWriter = new BinaryWriter();

        // Write the biomes id
        for (int i = 0; i < BIOME_COUNT; i++) {
            final byte id = (byte) biomes[i].getId();
            binaryWriter.writeByte(id);
        }

        binaryWriter.writeBoolean(populated);
        binaryWriter.writeBoolean(generated);

        boolean hasChunkData = data instanceof SerializableData && !data.isEmpty();

        binaryWriter.writeBoolean(hasChunkData);
        if (hasChunkData) {
            binaryWriter.writeBytes(((SerializableData) data).getSerializedData(typeToIndexMap, false));
        }

        for (byte x = 0; x < CHUNK_SIZE_X; x++) {
            for (short y = 0; y < CHUNK_SIZE_Y; y++) {
                for (byte z = 0; z < CHUNK_SIZE_Z; z++) {
                    final int index = getBlockIndex(x, y, z);

                    final short blockStateId = blocksStateId[index];
                    final short customBlockId = customBlocksId[index];

                    // No block at the position
                    if (blockStateId == 0 && customBlockId == 0)
                        continue;

                    // Chunk coordinates
                    binaryWriter.writeShort((short) index);

                    // Block ids
                    binaryWriter.writeShort(blockStateId);
                    binaryWriter.writeShort(customBlockId);

                    // Data
                    final Data data = getBlockData(index);
                    final boolean hasBlockData = data instanceof SerializableData && !data.isEmpty();
                    binaryWriter.writeBoolean(hasBlockData);
                    if (hasBlockData) {
                        // Get the un-indexed data
                        final byte[] serializedData = ((SerializableData) data).getSerializedData(typeToIndexMap, false);
                        binaryWriter.writeBytes(serializedData);
                    }
                }
            }
        }

        // If the chunk data contains SerializableData type, it needs to be added in the header
        BinaryWriter indexWriter = new BinaryWriter();
        final boolean hasDataIndex = !typeToIndexMap.isEmpty();
        indexWriter.writeBoolean(hasDataIndex);
        if (hasDataIndex) {
            // Get the index buffer (prefixed by true to say that the chunk contains data indexes)
            SerializableData.writeDataIndexHeader(indexWriter, typeToIndexMap);
        }

        // Add the hasIndex field & the index header if it has it
        binaryWriter.writeAtStart(indexWriter);

		//Metadata
		{
			BinaryWriter metadata = new BinaryWriter();
			metadata.writeInt(DATA_FORMAT_VERSION);
			metadata.writeInt(MinecraftServer.PROTOCOL_VERSION);
			binaryWriter.writeAtStart(metadata);
		}

        return binaryWriter.toByteArray();
    }

    @Override
    public void readChunk(BinaryReader reader, ChunkCallback callback) {
        // Used for blocks data
        Object2ShortMap<String> typeToIndexMap = new Object2ShortOpenHashMap<>();

        ChunkBatch chunkBatch = instance.createChunkBatch(this);
        try {
			final int version = reader.readInteger();
			final boolean latestStructure = version == DATA_FORMAT_VERSION;
			if (!latestStructure) {
                // Incorrect data format version
                //Todo convert
                throw new RuntimeException("Unsupported data format");
            }
			boolean latestIds = true;
				final int protocolVersion = reader.readInteger();
				//todo do something if incorrect version
				latestIds = protocolVersion == MinecraftServer.PROTOCOL_VERSION;

			// Get if the chunk has data indexes (used for chunk & blocks data)
			final boolean hasDataIndex = reader.readBoolean();
			if (hasDataIndex) {
                // Get the data indexes which will be used to read all the individual data
                typeToIndexMap = SerializableData.readDataIndexes(reader);
            }

            for (int i = 0; i < BIOME_COUNT; i++) {
                final byte id = reader.readByte();
				final Biome biome = BIOME_MANAGER.getById(id);
				this.biomes[i] = biome != null ? biome : Biome.PLAINS;
			}

				setPopulated(reader.readBoolean());
				setGenerated(reader.readBoolean());
				boolean hasChunkData = reader.readBoolean();

				if (hasChunkData) {
                    data = new SerializableDataImpl();
                    ((SerializableDataImpl) data).readSerializedData(reader, typeToIndexMap);
                }
            while (true) {
                // Position
                final short index = reader.readShort();
                final byte x = ChunkUtils.blockIndexToChunkPositionX(index);
                final short y = ChunkUtils.blockIndexToChunkPositionY(index);
                final byte z = ChunkUtils.blockIndexToChunkPositionZ(index);

                // Block type
                final short blockStateId = reader.readShort();
                final short customBlockId = reader.readShort();

                // Data
                SerializableData data = null;
                {
                    final boolean hasBlockData = reader.readBoolean();
                    // Data deserializer
					if (hasBlockData && hasDataIndex) {
                        // Read the data with the deserialized index map
                        data = new SerializableDataImpl();
                        data.readSerializedData(reader, typeToIndexMap);
                    }
                }

                if (customBlockId != 0) {
                    chunkBatch.setSeparateBlocks(x, y, z, blockStateId, customBlockId, data);
                } else {
                    chunkBatch.setBlockStateId(x, y, z, blockStateId, data);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // Finished reading
        }

        // Place all the blocks from the batch
        chunkBatch.unsafeFlush(callback); // Success, null if file isn't properly encoded
    }

    @Override
    protected ChunkDataPacket getFreshPacket() {
        ChunkDataPacket fullDataPacket = new ChunkDataPacket();
        fullDataPacket.biomes = biomes.clone();
        fullDataPacket.chunkX = chunkX;
        fullDataPacket.chunkZ = chunkZ;
        fullDataPacket.blocksStateId = blocksStateId.clone();
        fullDataPacket.customBlocksId = customBlocksId.clone();
        fullDataPacket.blockEntities = new HashSet<>(blockEntities);
        fullDataPacket.blocksData = new Int2ObjectOpenHashMap<>(blocksData);
        return fullDataPacket;
    }

}
