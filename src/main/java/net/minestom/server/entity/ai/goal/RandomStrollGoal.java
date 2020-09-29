package net.minestom.server.entity.ai.goal;

import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.ai.GoalSelector;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomStrollGoal extends GoalSelector {

    private static final Random RANDOM = new Random();

    private static final long DELAY = 5000;

    private final int radius;
    private final float chance;
    private final boolean refreshNearby;
    private List<Position> closePositions;

    private long lastStroll;

    public RandomStrollGoal(EntityCreature entityCreature, int radius, float chance, boolean refreshNearby) {
        super(entityCreature);
        this.radius = radius;
        this.chance = MathUtils.clampFloat(chance, 0, 1);
        if (!refreshNearby)
            this.closePositions = getNearbyBlocks(radius);
        this.refreshNearby = refreshNearby;
        entityCreature.setDebugPathfinder(true);
    }

    @Override
    public boolean shouldStart() {
        return System.currentTimeMillis() - lastStroll >= DELAY && RANDOM.nextFloat() <= chance;
    }

    @Override
    public void start() {
        if (refreshNearby) {
            closePositions = getNearbyBlocksFast(radius);
        }
        Collections.shuffle(closePositions);
        for (Position position : closePositions) {
            final Position target = position.clone();
            final boolean result = entityCreature.setPathTo(target);
            if (result) {
                break;
            }
        }

    }

    @Override
    public void tick(long time) {

    }

    @Override
    public boolean shouldEnd() {
        return entityCreature.getPath() == null;
    }

    @Override
    public void end() {
        this.lastStroll = System.currentTimeMillis();
    }

    public int getRadius() {
        return radius;
    }

    private List<Position> getNearbyBlocks(int radius) {
        final Position clone = entityCreature.getPosition().clone();
        List<Position> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    if (!Block.fromStateId(entityCreature.getInstance().getBlockStateId(clone.getX() + x, clone.getY() + y, clone.getZ() + z)).isSolid() && Block.fromStateId(entityCreature.getInstance().getBlockStateId(clone.getX() + x, clone.getY() + y - 1, clone.getZ() + z)).isSolid()) {
                        final Position position = new Position(clone.getX() + x, clone.getY() + y, clone.getZ() + z);
                        blocks.add(position);
                        break;
                    }
                }
            }
        }
        return blocks;
    }

    private List<Position> getNearbyBlocksFast(int radius) {
        final Position clone = entityCreature.getPosition().clone();
        List<Position> blocks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int x = RANDOM.nextInt(radius);
            int z = RANDOM.nextInt(radius);
            for (int y = -radius; y <= radius; y++) {
                if (!Block.fromStateId(entityCreature.getInstance().getBlockStateId(clone.getX() + x, clone.getY() + y, clone.getZ() + z)).isSolid() && Block.fromStateId(entityCreature.getInstance().getBlockStateId(clone.getX() + x, clone.getY() + y - 1, clone.getZ() + z)).isSolid()) {
                    final Position position = new Position(clone.getX() + x, clone.getY() + y, clone.getZ() + z);
                    blocks.add(position);
                    break;
                }
            }
        }
        return blocks;
    }

}
