package mod.kelvinlby.opencrafter.core.config.handview;

/** First-person hand rendering tweaks. Only the view changes; nothing sent to the server does. */
public final class HandViewConfig {
	/** Global toggle: when off, every hand view setting is ignored and vanilla renders. */
	public boolean enabled = false;

	/** Hands follow the rotation last sent to the server when another mod spoofs it. */
	public boolean serverRotations = false;
	/** Hands stay fixed to the camera instead of lagging behind turns. */
	public boolean noRotationDelay = false;
	public boolean hideEmptyHand = false;
	/** A newly held item shows at once instead of its swap animation. */
	public boolean skipSwapping = false;
	/** Bare arms always show the slim default skin (Alex) instead of the player's own. */
	public boolean removeSkin = false;

	public SwingConfig swing = new SwingConfig();
	public HandTransformConfig mainHand = new HandTransformConfig();
	public HandTransformConfig offHand = new HandTransformConfig();
	public HandTransformConfig arm = new HandTransformConfig();
	public EatingConfig eating = new EatingConfig();
}
