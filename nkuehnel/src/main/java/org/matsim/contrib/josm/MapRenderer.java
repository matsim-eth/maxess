package org.matsim.contrib.josm;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.LinkImpl;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.mappaint.LabelCompositionStrategy;
import org.openstreetmap.josm.gui.mappaint.TextElement;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * the MATSim MapRenderer. Draws ways that correspond to an existing MATSim link
 * in a MATSim blue color. Also offers offset for overlapping links as well as
 * the option to show MATSim ids on ways
 * 
 */
public class MapRenderer extends StyledMapRenderer {

	public MapRenderer(Graphics2D arg0, NavigatableComponent arg1, boolean arg2) {
		super(arg0, arg1, arg2);
	}

	/**
	 * draw way
	 * 
	 * @param showOrientation
	 *            show arrows that indicate the technical orientation of the way
	 *            (defined by order of nodes)
	 * @param showOneway
	 *            show symbols that indicate the direction of the feature, e.g.
	 *            oneway street or waterway
	 * @param onewayReversed
	 *            for oneway=-1 and similar
	 */
	public void drawWay(Way way, Color color, BasicStroke line,
			BasicStroke dashes, Color dashedColor, float offset,
			boolean showOrientation, boolean showHeadArrowOnly,
			boolean showOneway, boolean onewayReversed) {

		Layer layer = Main.main.getActiveLayer();
		if (layer instanceof NetworkLayer) {
			Map<Way, List<Link>> way2Links = ((NetworkLayer) layer)
					.getWay2Links();
			if (way2Links.containsKey(way)) {
				if (!way2Links.get(way).isEmpty()) {
					if (!way.isSelected()) {
						if (Properties.showIds) {
							drawTextOnPath(way,
									new TextElement(Properties.getInstance(),
											Properties.FONT, 0,
											textOffset(way),
											Properties.MATSIMCOLOR, 0.f, null));
						}
						super.drawWay(way, Properties.MATSIMCOLOR, line,
								dashes, dashedColor, Properties.wayOffset*-1,
								showOrientation, showHeadArrowOnly, showOneway,
								onewayReversed);
						return;
					} else {
						if (Properties.showIds) {
							drawTextOnPath(way,
									new TextElement(Properties.getInstance(),
											Properties.FONT, 0,
											textOffset(way), selectedColor,
											0.f, null));
						}
					}
				}
			}
		}
		super.drawWay(way, color, line, dashes, dashedColor,
				Properties.wayOffset*-1, showOrientation, showHeadArrowOnly,
				showOneway, onewayReversed);
	}

	private int textOffset(Way way) {
		int offset = -15;

		if (way.firstNode().getUniqueId() < way.lastNode().getUniqueId()) {
			offset *= -1;
		}
		return offset;
	}

	static class Properties extends LabelCompositionStrategy implements
			PreferenceChangedListener {

		private final static Properties INSTANCE = new Properties();
		final static Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
		final static Color MATSIMCOLOR = new Color(80, 145, 190);
		static boolean showIds = Main.pref.getBoolean("matsim_showIds", false);
		static float wayOffset = ((float) Main.pref.getDouble(
				"matsim_wayOffset", 0));

		public static void initialize() {
			Main.pref.addPreferenceChangeListener(INSTANCE);
		}

		@Override
		public void preferenceChanged(PreferenceChangeEvent e) {
			if (e.getKey().equalsIgnoreCase("matsim_showIds")) {
				showIds = Main.pref.getBoolean("matsim_showIds");
			}
			if (e.getKey().equalsIgnoreCase("matsim_wayOffset")) {
				wayOffset = ((float) (Main.pref
						.getDouble("matsim_wayOffset", 0)));
			}
		}

		public static Properties getInstance() {
			return INSTANCE;
		}

		@Override
		public String compose(OsmPrimitive prim) {
			Layer layer = Main.main.getActiveLayer();
			StringBuilder sB = new StringBuilder();
			if (((NetworkLayer) layer).getWay2Links().containsKey(prim)) {
				for (Link link: ((NetworkLayer) layer).getWay2Links().get(prim)) {
					sB.append(" [").append(((LinkImpl) link).getOrigId()).append("] ");
				}
			}
			return sB.toString();
		}
	}
}
