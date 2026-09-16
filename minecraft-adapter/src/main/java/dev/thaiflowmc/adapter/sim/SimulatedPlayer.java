package dev.thaiflowmc.adapter.sim;

import dev.thaiflowmc.api.Log;
import dev.thaiflowmc.api.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimulatedPlayer implements Player {

    private static final Logger LOG = LoggerFactory.getLogger(Log.MINECRAFT);

    private final String name;
    private double health = 20.0;
    private double x;
    private double y;
    private double z;

    public SimulatedPlayer(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getHealth() {
        return health;
    }

    @Override
    public void say(String message) {
        LOG.info("<{}> {}", name, message);
    }

    @Override
    public void teleport(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        LOG.info("{} teleported to ({}, {}, {})", name, x, y, z);
    }
}
