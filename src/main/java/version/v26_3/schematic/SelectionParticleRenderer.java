package version.v26_3.schematic;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDouble3D;
import core.messages.Messages;
import core.schematic.BoundingBox;
import core.schematic.SelectionState;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.builder.LevelParticlesBuilder;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.proxy.PacketInjector;
import version.v26_3.world.WorldManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders the current selection as a particle outline on the client, similar to the Iris wand
 * selection. Draws the 12 edges of the selection's bounding box with flame particles.
 *
 * <p>Flame is a simple particle with no extra data payload, which avoids version-specific
 * dust color format differences (3 floats vs packed int) that caused crashes on 1.21.2+.
 *
 * <p>The renderer runs on a {@link ScheduledExecutorService} that ticks every 5 ticks (250 ms).
 * It is started when selection mode is enabled (and both positions are set) and stopped when
 * selection mode is disabled or the selection is cleared.
 *
 * <p>All particles are sent directly to the client via {@link PacketInjector} — they never reach
 * the server.
 */
public class SelectionParticleRenderer {
    private static final double STEP = 0.5; // blocks between particles along each edge
    private static final long PERIOD_MS = 250; // 5 ticks
    private static final double MAX_RENDER_DIST_SQUARED = 4096.0; // 64 blocks
    private static final String FLAME_PARTICLE = "minecraft:flame";

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean loggedDiagnostics = new AtomicBoolean(false);
    private final SelectionState state;
    private final SelectionFeedback feedback = new SelectionFeedback();
    private long startTime;

    public SelectionParticleRenderer(SelectionState state) {
        this.state = state;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
            new Thread(r, "Selection Particle Renderer"));
    }

    /**
     * Start the repeating particle task. Safe to call multiple times — only the first call
     * starts the scheduler.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = System.currentTimeMillis();
            scheduler.scheduleAtFixedRate(this::renderFrame, 0, PERIOD_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the repeating particle task. Safe to call multiple times.
     */
    public void stop() {
        // CAS from true -> false; the next renderFrame will be a no-op.
        running.compareAndSet(true, false);
    }

    /**
     * Shut down the scheduler permanently (e.g. on disconnect).
     */
    public void shutdown() {
        running.set(false);
        scheduler.shutdownNow();
    }

    private void renderFrame() {
        if (!running.get() || !state.isEnabled() || state.getPos1() == null) {
            return;
        }

        PacketInjector injector = VersionAccessors.injector();
        if (injector == null) {
            return;
        }

        int flameId = ParticleRegistry.getInstance().getId(FLAME_PARTICLE);
        if (flameId < 0) {
            return; // particle registry not yet loaded
        }

        boolean logFirstPacket = loggedDiagnostics.compareAndSet(false, true);
        if (logFirstPacket) {
            System.out.println(Messages.console("console.particle.protocol", Config.getProtocolVersion(), flameId, ParticleRegistry.getInstance().isLoaded()));
        }

        CoordinateDouble3D playerPos = WorldManager.getInstance().getPlayerPosition().toDouble();
        double px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();

        List<double[]> points;
        if (state.hasCompleteSelection()) {
            BoundingBox box = state.toBoundingBox();
            Coordinate3D min = box.getMin();
            Coordinate3D max = box.getMax();
            // +1 because max is inclusive — we want to draw the far corner too
            double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
            double x1 = max.getX() + 1, y1 = max.getY() + 1, z1 = max.getZ() + 1;
            points = edgePoints(x0, y0, z0, x1, y1, z1);
        } else {
            // only pos1 set: draw a single point at the pos1 block corner
            Coordinate3D p = state.getPos1();
            points = List.of(new double[] { p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5 });
        }

        List<PacketBuilder> packets = new ArrayList<>();
        for (double[] point : points) {
            double dx = point[0] - px, dy = point[1] - py, dz = point[2] - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > MAX_RENDER_DIST_SQUARED) {
                continue;
            }

            PacketBuilder pb = LevelParticlesBuilder.buildSingle(flameId, point[0], point[1], point[2]);
            packets.add(pb);

            if (logFirstPacket) {
                logFirstPacket = false;
                byte[] raw = pb.toArray();
                StringBuilder hex = new StringBuilder();
                for (byte b : raw) {
                    hex.append(String.format("%02X ", b));
                }
                System.out.println(Messages.console("console.particle.first_body", raw.length, hex));
            }
        }

        // Send all particles in a single burst so they appear simultaneously,
        // not sequentially (the injector queue is throttled to 100 packets per flush).
        if (!packets.isEmpty()) {
            version.v26_3.proxy.EncryptionManager em = VersionAccessors.encryptionManager();
            if (em != null) {
                try {
                    em.streamToClientBatch(packets);
                } catch (Exception e) {
                    // fall back to enqueuing if direct write fails
                    for (PacketBuilder pb : packets) {
                        injector.enqueuePacket(pb);
                    }
                }
            } else {
                for (PacketBuilder pb : packets) {
                    injector.enqueuePacket(pb);
                }
            }
        }

        // Continuously show the current selection state on the action bar so the player
        // always knows which corners are set and where.
        sendSelectionStatus();
    }

    private void sendSelectionStatus() {
        Coordinate3D p1 = state.getPos1();
        Coordinate3D p2 = state.getPos2();
        if (p1 == null && p2 == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (p1 != null) {
            sb.append(Messages.server("server.selection.particle_pos1", p1.getX(), p1.getY(), p1.getZ()));
        }
        if (p2 != null) {
            if (sb.length() > 0) {
                sb.append(Messages.server("server.selection.particle_separator"));
            }
            sb.append(Messages.server("server.selection.particle_pos2", p2.getX(), p2.getY(), p2.getZ()));
        }
        feedback.send(sb.toString());
    }

    /**
     * Yields points along all 12 edges of the axis-aligned box defined by corners (x0,y0,z0)
     * and (x1,y1,z1), stepping by {@link #STEP} blocks.
     */
    private static List<double[]> edgePoints(double x0, double y0, double z0,
                                             double x1, double y1, double z1) {
        List<double[]> points = new ArrayList<>();
        // 4 edges along X
        addLine(points, x0, y0, z0, x1, y0, z0);
        addLine(points, x0, y0, z1, x1, y0, z1);
        addLine(points, x0, y1, z0, x1, y1, z0);
        addLine(points, x0, y1, z1, x1, y1, z1);
        // 4 edges along Y
        addLine(points, x0, y0, z0, x0, y1, z0);
        addLine(points, x0, y0, z1, x0, y1, z1);
        addLine(points, x1, y0, z0, x1, y1, z0);
        addLine(points, x1, y0, z1, x1, y1, z1);
        // 4 edges along Z
        addLine(points, x0, y0, z0, x0, y0, z1);
        addLine(points, x0, y1, z0, x0, y1, z1);
        addLine(points, x1, y0, z0, x1, y0, z1);
        addLine(points, x1, y1, z0, x1, y1, z1);
        return points;
    }

    private static void addLine(List<double[]> out,
                                double x0, double y0, double z0,
                                double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            out.add(new double[] { x0, y0, z0 });
            return;
        }
        double ux = dx / len, uy = dy / len, uz = dz / len;
        for (double d = 0; d <= len; d += STEP) {
            out.add(new double[] { x0 + ux * d, y0 + uy * d, z0 + uz * d });
        }
    }
}
