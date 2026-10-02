package com.urntt.airjump.config;

import com.urntt.airjump.ServerAddresses;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Edits the server list used by the whitelist and blacklist modes, modeled after vanilla's game rule screen:
 * "Done" saves and is disabled while an address is invalid, and "Cancel" or Escape discards the changes.
 */
public final class ServerListScreen extends Screen {
	private static final Component TITLE = Component.translatable("options.airjump.servers.title");
	private static final Component ADD = Component.translatable("options.airjump.servers.add");
	private static final Component REMOVE = Component.translatable("options.airjump.servers.remove");
	private static final Component ADDRESS_HINT = Component.translatable("options.airjump.servers.hint")
			.withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final Component ADDRESS_NARRATION = Component.translatable("options.airjump.servers.address");
	private static final int INVALID_TEXT_COLOR = 0xFFFF0000;
	/** Longest address an entry accepts; long enough for any host name with a port. */
	private static final int MAX_ADDRESS_LENGTH = 261;
	private static final int ROW_WIDTH = 310;
	private static final int ROW_HEIGHT = 24;
	private static final int REMOVE_BUTTON_WIDTH = 50;
	private static final int SPACING = 4;

	private final Screen parent;
	private final AirJumpConfig config;
	private final HeaderAndFooterLayout layout;
	private final Set<AddressEntry> invalidEntries = new HashSet<>();
	private @Nullable AddressList addressList;
	private @Nullable Button doneButton;

	public ServerListScreen(final Screen parent, final AirJumpConfig config) {
		super(TITLE);
		this.parent = parent;
		this.config = config;
		this.layout = new HeaderAndFooterLayout(this, 12 + 9 + SPACING + 9, 33);
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(SPACING));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(TITLE, this.font));
		header.addChild(new StringWidget(this.config.multiplayerMode().description().copy().withStyle(ChatFormatting.GRAY),
				this.font));

		this.invalidEntries.clear();
		this.addressList = this.layout.addToContents(new AddressList(this.config.servers()));

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(ADD, button -> this.addAddress()).width(100).build());
		this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose())
				.width(100).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(100).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.updateDoneButton();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.addressList != null) {
			this.addressList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private void saveAndClose() {
		if (this.addressList != null) {
			this.config.setServers(this.addressList.children().stream().map(AddressEntry::address).toList());
		}
		this.onClose();
	}

	private void addAddress() {
		if (this.addressList == null) {
			return;
		}
		AddressEntry entry = this.addressList.add("");
		this.setFocused(this.addressList);
		this.addressList.setFocused(entry);
		entry.setFocused(entry.addressBox);
	}

	private void updateValidity(final AddressEntry entry, final boolean valid) {
		if (valid) {
			this.invalidEntries.remove(entry);
		} else {
			this.invalidEntries.add(entry);
		}
		this.updateDoneButton();
	}

	private void updateDoneButton() {
		if (this.doneButton != null) {
			this.doneButton.active = this.invalidEntries.isEmpty();
		}
	}

	private final class AddressList extends ContainerObjectSelectionList<AddressEntry> {
		private AddressList(final List<String> addresses) {
			super(Minecraft.getInstance(), ServerListScreen.this.width, ServerListScreen.this.layout.getContentHeight(),
					ServerListScreen.this.layout.getHeaderHeight(), ROW_HEIGHT);
			addresses.forEach(this::add);
		}

		private AddressEntry add(final String address) {
			AddressEntry entry = new AddressEntry(address);
			this.addEntry(entry);
			this.scrollToEntry(entry);
			return entry;
		}

		private void remove(final AddressEntry entry) {
			ServerListScreen.this.updateValidity(entry, true);
			this.removeEntry(entry);
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}
	}

	private final class AddressEntry extends ContainerObjectSelectionList.Entry<AddressEntry> {
		private final EditBox addressBox;
		private final Button removeButton;

		private AddressEntry(final String address) {
			this.addressBox = new EditBox(ServerListScreen.this.font, ROW_WIDTH - REMOVE_BUTTON_WIDTH - SPACING, 20,
					ADDRESS_NARRATION);
			this.addressBox.setMaxLength(MAX_ADDRESS_LENGTH);
			this.addressBox.setHint(ADDRESS_HINT);
			this.addressBox.setValue(address);
			this.addressBox.setResponder(this::validate);
			this.validate(address);
			this.removeButton = Button.builder(REMOVE, button -> {
				if (ServerListScreen.this.addressList != null) {
					ServerListScreen.this.addressList.remove(this);
				}
			}).width(REMOVE_BUTTON_WIDTH).build();
		}

		/** The trimmed address; blank entries are dropped when saving. */
		private String address() {
			return this.addressBox.getValue().trim();
		}

		private void validate(final String text) {
			boolean valid = text.isBlank() || ServerAddresses.isValid(text);
			this.addressBox.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
			ServerListScreen.this.updateValidity(this, valid);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.addressBox, this.removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.addressBox, this.removeButton);
		}

		@Override
		public void extractContent(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
				final boolean hovered, final float a) {
			this.addressBox.setPosition(this.getContentX(), this.getContentY());
			this.removeButton.setPosition(this.getContentRight() - REMOVE_BUTTON_WIDTH, this.getContentY());
			this.addressBox.extractRenderState(graphics, mouseX, mouseY, a);
			this.removeButton.extractRenderState(graphics, mouseX, mouseY, a);
		}
	}
}
