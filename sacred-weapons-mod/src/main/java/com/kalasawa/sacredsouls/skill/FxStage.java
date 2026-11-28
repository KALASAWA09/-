package com.kalasawa.sacredsouls.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The 3D models and magic circles of one skill cast: the mod's own Blockbench item models shown as item displays,
 * moved with the displays' smooth interpolation. Everything carries a tag so it is always cleaned up.
 */
public final class FxStage {
	/** Every entity any skill stage spawns carries this tag. */
	public static final String TAG = "sacredsouls_fx";
	private static int counter;

	private final ServerLevel level;
	private final String tag;
	/** When each model was summoned, so a move in the same tick waits for the clients to know it. */
	private final Map<String, Long> born = new HashMap<>();
	private final List<Object[]> pending = new ArrayList<>();

	public FxStage(ServerLevel level) {
		this.level = level;
		this.tag = "ssf" + (++counter) + "_" + (level.getGameTime() % 100000);
	}

	/** Runs moves that were held back a tick. Call once at the start of every tick of the cast. */
	public void tick() {
		long now = level.getGameTime();
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Object[]> it = pending.iterator(); it.hasNext();) {
			Object[] p = it.next();
			if ((long) p[0] <= now) {
				it.remove();
				due.add((Runnable) p[1]);
			}
		}
		due.forEach(Runnable::run);
	}

	/** Summons one of the mod's models at a point, with a rotation, an offset and a scale. */
	public void model(String name, String model, Vec3 at, float[] q, Vec3 offset, double sx, double sy, double sz) {
		model(name, model, at, q, offset, sx, sy, sz, false);
	}

	/** As above; a billboard model always turns its face to whoever looks at it. */
	public void model(String name, String model, Vec3 at, float[] q, Vec3 offset, double sx, double sy, double sz, boolean billboard) {
		kill(name);
		born.put(name, level.getGameTime());
		cmd(String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_%s\"],"
						+ "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"sacredsouls:%s\"}},"
						+ "item_display:\"none\",billboard:\"%s\",brightness:{sky:15,block:15},view_range:4f,transformation:%s}",
				at.x, at.y, at.z, TAG, tag, tag, name, model, billboard ? "center" : "fixed", transformation(q, offset, sx, sy, sz)));
	}

	public void model(String name, String model, Vec3 at, float[] q, Vec3 offset, double scale) {
		model(name, model, at, q, offset, scale, scale, scale);
	}

	/** Moves a model to a new rotation, offset and scale over the given ticks. */
	public void move(String name, float[] q, Vec3 offset, double sx, double sy, double sz, int ticks) {
		String command = String.format(Locale.ROOT,
				"data merge entity @e[tag=%s_%s,limit=1] {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
				tag, name, Math.max(0, ticks), transformation(q, offset, sx, sy, sz));
		Long when = born.get(name);
		if (when != null && when >= level.getGameTime()) {
			later(1, () -> cmd(command));
		} else {
			cmd(command);
		}
	}

	public void move(String name, float[] q, Vec3 offset, double scale, int ticks) {
		move(name, q, offset, scale, scale, scale, ticks);
	}

	/** Runs something a number of ticks from now, as long as the stage lives. */
	public void later(int ticks, Runnable action) {
		pending.add(new Object[] {level.getGameTime() + ticks, action});
	}

	public void killLater(String name, int ticks) {
		later(ticks, () -> kill(name));
	}

	public void kill(String name) {
		if (born.remove(name) != null) {
			cmd(String.format(Locale.ROOT, "kill @e[tag=%s_%s]", tag, name));
		}
	}

	public void killAll() {
		pending.clear();
		if (!born.isEmpty()) {
			born.clear();
			cmd("kill @e[tag=" + tag + "]");
		}
	}

	// ---------------------------------------------------------------- rotations

	/** Turned around the vertical by an angle. A model's +x points along (cos a, -sin a) in x and z. */
	public static float[] yaw(double angle) {
		return new float[] {0, (float) Math.sin(angle / 2), 0, (float) Math.cos(angle / 2)};
	}

	/** Turned so the model's +x points along a horizontal direction. */
	public static float[] facing(Vec3 direction) {
		return yaw(-Math.atan2(direction.z, direction.x));
	}

	/** A flat model (a circle) laid on the ground, turned by an angle. */
	public static float[] flat(double turn) {
		return mul(yaw(turn), axis(1, 0, 0, -Math.PI / 2));
	}

	public static float[] axis(double ax, double ay, double az, double angle) {
		double len = Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0E-6) {
			return new float[] {0, 0, 0, 1};
		}
		double s = Math.sin(angle / 2) / len;
		return new float[] {(float) (ax * s), (float) (ay * s), (float) (az * s), (float) Math.cos(angle / 2)};
	}

	/** Tilted outward from the vertical toward a horizontal angle (cos a, 0, sin a), then spun around its own axis. */
	public static float[] tiltOut(double a, double tilt, double spin) {
		return mul(axis(Math.sin(a), 0, -Math.cos(a), tilt), yaw(spin));
	}

	/** Hamilton product: apply b first, then a. */
	public static float[] mul(float[] a, float[] b) {
		return new float[] {
				a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
				a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
				a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
				a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	private static String transformation(float[] q, Vec3 t, double sx, double sy, double sz) {
		return String.format(Locale.ROOT,
				"{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}",
				q[0], q[1], q[2], q[3], t.x, t.y, t.z, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz));
	}

	private void cmd(String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
	}

	/** Removes stage entities left behind by a crash or an interrupted cast, in every loaded dimension. */
	public static void cleanupAll(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
					"kill @e[tag=" + TAG + "]");
		}
	}
}
