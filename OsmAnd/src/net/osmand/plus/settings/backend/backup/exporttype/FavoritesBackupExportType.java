package net.osmand.plus.settings.backend.backup.exporttype;

import androidx.annotation.NonNull;

import net.osmand.plus.R;
import net.osmand.plus.settings.backend.backup.SettingsItemType;

class FavoritesBackupExportType extends ExportType {

	public FavoritesBackupExportType() {
		super(R.string.favorites_backup, R.drawable.ic_action_folder_favorites, SettingsItemType.FILE);
	}

	@NonNull
	@Override
	public String getId() {
		return "FAVORITES_BACKUP";
	}
}