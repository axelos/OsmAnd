package net.osmand.plus.track.helpers;

import static net.osmand.IndexConstants.GPX_FILE_EXT;
import static net.osmand.binary.RouteDataObject.HEIGHT_UNDEFINED;
import static net.osmand.plus.utils.UiUtilities.CompoundButtonType.PROFILE_DEPENDENT;
import static net.osmand.router.network.NetworkRouteSelector.RouteKey;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import net.osmand.CallbackWithObject;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.gpx.GPXFile;
import net.osmand.gpx.GPXTrackAnalysis;
import net.osmand.gpx.GPXUtilities;
import net.osmand.gpx.GPXUtilities.Track;
import net.osmand.gpx.GPXUtilities.TrkSegment;
import net.osmand.gpx.GPXUtilities.WptPt;
import net.osmand.plus.OsmAndConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.activities.ActivityResultListener;
import net.osmand.plus.activities.ActivityResultListener.OnActivityResultListener;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.SelectGpxTrackBottomSheet;
import net.osmand.plus.importfiles.ImportHelper;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu.ChartPointLayer;
import net.osmand.plus.myplaces.SaveCurrentTrackTask;
import net.osmand.plus.plugins.PluginsFragment;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.monitoring.OsmandMonitoringPlugin;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.track.GpxSelectionParams;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.SaveGpxAsyncTask;
import net.osmand.plus.track.SaveGpxAsyncTask.SaveGpxListener;
import net.osmand.plus.track.fragments.TrackMenuFragment;
import net.osmand.plus.track.helpers.GPXDatabase.GpxDataItem;
import net.osmand.plus.track.helpers.GpsFilterHelper.GpsFilter;
import net.osmand.plus.track.helpers.GpxDbHelper.GpxDataItemCallback;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.FileUtils;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter;
import net.osmand.plus.widgets.ctxmenu.CtxMenuUtils;
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

public class GpxUiHelper {

	private static final Log LOG = PlatformUtil.getLog(GpxUiHelper.class);

	private static final int OPEN_GPX_DOCUMENT_REQUEST = 1005;

	public static final long SECOND_IN_MILLIS = 1000L;
	public static final long HOUR_IN_MILLIS = 60 * 60 * SECOND_IN_MILLIS;


	public static String getDescription(OsmandApplication app, GPXFile result, File f, boolean html) {
		GPXTrackAnalysis analysis = result.getAnalysis(f == null ? 0 : f.lastModified());
		return getDescription(app, analysis, html);
	}

	public static String getDescription(OsmandApplication app, TrkSegment t, boolean html) {
		return getDescription(app, GPXTrackAnalysis.segment(0, t), html);
	}

	public static String getColorValue(String clr, String value, boolean html) {
		if (!html) {
			return value;
		}
		return "<font color=\"" + clr + "\">" + value + "</font>";
	}

	public static String getColorValue(String clr, String value) {
		return getColorValue(clr, value, true);
	}

	public static String getDescription(OsmandApplication app, GPXTrackAnalysis analysis, boolean html) {
		StringBuilder description = new StringBuilder();
		String nl = html ? "<br/>" : "\n";
		String timeSpanClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_time_span_color));
		String distanceClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_distance_color));
		String speedClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_speed));
		String ascClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_altitude_asc));
		String descClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_altitude_desc));
		// OUTPUT:
		// 1. Total distance, Start time, End time
		description.append(app.getString(R.string.gpx_info_distance, getColorValue(distanceClr,
						OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app), html),
				getColorValue(distanceClr, analysis.points + "", html)));
		if (analysis.totalTracks > 1) {
			description.append(nl).append(app.getString(R.string.gpx_info_subtracks, getColorValue(speedClr, analysis.totalTracks + "", html)));
		}
		if (analysis.wptPoints > 0) {
			description.append(nl).append(app.getString(R.string.gpx_info_waypoints, getColorValue(speedClr, analysis.wptPoints + "", html)));
		}
		if (analysis.isTimeSpecified()) {
			description.append(nl).append(app.getString(R.string.gpx_info_start_time, analysis.startTime));
			description.append(nl).append(app.getString(R.string.gpx_info_end_time, analysis.endTime));
		}

		// 2. Time span
		if (analysis.timeSpan > 0 && analysis.timeSpan != analysis.timeMoving) {
			String formatDuration = Algorithms.formatDuration((int) (analysis.timeSpan / 1000.0f + 0.5), app.accessibilityEnabled());
			description.append(nl).append(app.getString(R.string.gpx_timespan,
					getColorValue(timeSpanClr, formatDuration, html)));
		}

		// 3. Time moving, if any
		if (analysis.isTimeMoving()) {
			//Next few lines for Issue 3222 heuristic testing only
			//final String formatDuration0 = Algorithms.formatDuration((int) (analysis.timeMoving0 / 1000.0f + 0.5), app.accessibilityEnabled());
			//description.append(nl).append(app.getString(R.string.gpx_timemoving,
			//		getColorValue(timeSpanClr, formatDuration0, html)));
			//description.append(" (" + getColorValue(distanceClr, OsmAndFormatter.getFormattedDistance(analysis.totalDistanceMoving0, app), html) + ")");
			String formatDuration = Algorithms.formatDuration((int) (analysis.timeMoving / 1000.0f + 0.5), app.accessibilityEnabled());
			description.append(nl).append(app.getString(R.string.gpx_timemoving,
					getColorValue(timeSpanClr, formatDuration, html)));
			description.append(" (" + getColorValue(distanceClr, OsmAndFormatter.getFormattedDistance(analysis.totalDistanceMoving, app), html) + ")");
		}

		// 4. Elevation, eleUp, eleDown, if recorded
		if (analysis.isElevationSpecified()) {
			description.append(nl);
			description.append(app.getString(R.string.gpx_info_avg_altitude,
					getColorValue(speedClr, OsmAndFormatter.getFormattedAlt(analysis.avgElevation, app), html)));
			description.append(nl);
			String min = getColorValue(descClr, OsmAndFormatter.getFormattedAlt(analysis.minElevation, app), html);
			String max = getColorValue(ascClr, OsmAndFormatter.getFormattedAlt(analysis.maxElevation, app), html);
			String asc = getColorValue(ascClr, OsmAndFormatter.getFormattedAlt(analysis.diffElevationUp, app), html);
			String desc = getColorValue(descClr, OsmAndFormatter.getFormattedAlt(analysis.diffElevationDown, app), html);
			description.append(app.getString(R.string.gpx_info_diff_altitude, min + " - " + max));
			description.append(nl);
			description.append(app.getString(R.string.gpx_info_asc_altitude, "\u2193 " + desc + "   \u2191 " + asc + ""));
		}


		if (analysis.isSpeedSpecified()) {
			String avg = getColorValue(speedClr, OsmAndFormatter.getFormattedSpeed(analysis.avgSpeed, app), html);
			String max = getColorValue(ascClr, OsmAndFormatter.getFormattedSpeed(analysis.maxSpeed, app), html);
			description.append(nl).append(app.getString(R.string.gpx_info_average_speed, avg));
			description.append(nl).append(app.getString(R.string.gpx_info_maximum_speed, max));
		}
		return description.toString();
	}

	private static List<GPXInfo> listGpxInfo(OsmandApplication app, List<String> selectedGpxFiles, boolean absolutePath) {
		File gpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		List<GPXInfo> allGpxList = getSortedGPXFilesInfo(gpxDir, selectedGpxFiles, absolutePath);
		GPXInfo currentTrack = new GPXInfo(app.getString(R.string.show_current_gpx_title), null);
		currentTrack.setSelected(selectedGpxFiles.contains(""));
		allGpxList.add(0, currentTrack);
		return allGpxList;
	}

	public static AlertDialog selectGPXFile(Activity activity, boolean showCurrentGpx,
	                                        boolean multipleChoice,
	                                        CallbackWithObject<GPXFile[]> callbackWithObject,
	                                        boolean nightMode) {
		int dialogThemeRes = nightMode ? R.style.OsmandDarkTheme : R.style.OsmandLightTheme;
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		List<GPXInfo> list = getSortedGPXFilesInfo(dir, null, false);
		if (list.isEmpty()) {
			Toast.makeText(activity, R.string.gpx_files_not_found, Toast.LENGTH_LONG).show();
		}
		if (!list.isEmpty() || showCurrentGpx) {
			if (showCurrentGpx) {
				list.add(0, new GPXInfo(activity.getString(R.string.show_current_gpx_title), null));
			}

			ContextMenuAdapter adapter = createGpxContextMenuAdapter(app, list, false);
			return createDialog(activity, showCurrentGpx, multipleChoice, callbackWithObject, list, adapter, dialogThemeRes, nightMode);
		}
		return null;
	}

	public static void selectSingleGPXFile(FragmentActivity activity, boolean showCurrentGpx,
	                                       CallbackWithObject<GPXFile[]> callbackWithObject) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		int gpxDirLength = app.getAppPath(IndexConstants.GPX_INDEX_DIR).getAbsolutePath().length();
		List<SelectedGpxFile> selectedGpxFiles = app.getSelectedGpxHelper().getSelectedGPXFiles();
		List<GPXInfo> list = new ArrayList<>(selectedGpxFiles.size() + 1);
		if (!PluginsHelper.isActive(OsmandMonitoringPlugin.class)) {
			showCurrentGpx = false;
		}
		if (!selectedGpxFiles.isEmpty() || showCurrentGpx) {
			if (showCurrentGpx) {
				list.add(new GPXInfo(activity.getString(R.string.shared_string_currently_recording_track), null));
			}

			for (SelectedGpxFile selectedGpx : selectedGpxFiles) {
				GPXFile gpxFile = selectedGpx.getGpxFile();
				if (!gpxFile.showCurrentTrack && gpxFile.path.length() > gpxDirLength + 1) {
					list.add(new GPXInfo(gpxFile.path.substring(gpxDirLength + 1), new File(gpxFile.path)));
				}
			}
			SelectGpxTrackBottomSheet.showInstance(activity.getSupportFragmentManager(), showCurrentGpx, callbackWithObject, list);
		}
	}

	private static ContextMenuAdapter createGpxContextMenuAdapter(OsmandApplication app,
	                                                              List<GPXInfo> allGpxList,
	                                                              boolean needSelectItems) {
		ContextMenuAdapter adapter = new ContextMenuAdapter(app);
		fillGpxContextMenuAdapter(adapter, allGpxList, needSelectItems);
		return adapter;
	}

	private static void fillGpxContextMenuAdapter(ContextMenuAdapter adapter, List<GPXInfo> allGpxFiles,
	                                              boolean needSelectItems) {
		for (GPXInfo gpxInfo : allGpxFiles) {
			adapter.addItem(new ContextMenuItem(null)
					.setTitle(getGpxTitle(gpxInfo.getFileName()))
					.setSelected(needSelectItems && gpxInfo.isSelected())
					.setIcon(R.drawable.ic_action_polygom_dark));
		}
	}

	@NonNull
	public static String getGpxTitle(@Nullable String name) {
		return name != null ? Algorithms.getFileNameWithoutExtension(name) : "";
	}

	@NonNull
	public static String getGpxDirTitle(@Nullable String name) {
		if (Algorithms.isEmpty(name)) {
			return "";
		}
		return Algorithms.capitalizeFirstLetter(Algorithms.getFileNameWithoutExtension(name));
	}

	private static class DialogGpxDataItemCallback implements GpxDataItemCallback {

		private static final int UPDATE_GPX_ITEM_MSG_ID = OsmAndConstants.UI_HANDLER_LOCATION_SERVICE + 6;
		private static final long MIN_UPDATE_INTERVAL = 500;

		private final OsmandApplication app;
		private long lastUpdateTime;
		private boolean updateEnable = true;
		private ArrayAdapter<String> listAdapter;

		DialogGpxDataItemCallback(OsmandApplication app) {
			this.app = app;
		}

		public boolean isUpdateEnable() {
			return updateEnable;
		}

		public void setUpdateEnable(boolean updateEnable) {
			this.updateEnable = updateEnable;
		}

		public ArrayAdapter<String> getListAdapter() {
			return listAdapter;
		}

		public void setListAdapter(ArrayAdapter<String> listAdapter) {
			this.listAdapter = listAdapter;
		}

		private final Runnable updateItemsProc = new Runnable() {
			@Override
			public void run() {
				if (updateEnable) {
					lastUpdateTime = System.currentTimeMillis();
					listAdapter.notifyDataSetChanged();
				}
			}
		};

		@Override
		public boolean isCancelled() {
			return !updateEnable;
		}

		@Override
		public void onGpxDataItemReady(GpxDataItem item) {
			if (System.currentTimeMillis() - lastUpdateTime > MIN_UPDATE_INTERVAL) {
				updateItemsProc.run();
			}
			app.runMessageInUIThreadAndCancelPrevious(UPDATE_GPX_ITEM_MSG_ID, updateItemsProc, MIN_UPDATE_INTERVAL);
		}
	}

	private static AlertDialog createDialog(Activity activity,
	                                        boolean showCurrentGpx,
	                                        boolean multipleChoice,
	                                        CallbackWithObject<GPXFile[]> callbackWithObject,
	                                        List<GPXInfo> gpxInfoList,
	                                        ContextMenuAdapter adapter,
	                                        int themeRes,
	                                        boolean nightMode) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(activity, themeRes));
		final int layout = R.layout.gpx_track_item;
		DialogGpxDataItemCallback gpxDataItemCallback = new DialogGpxDataItemCallback(app);

		List<String> modifiableGpxFileNames = CtxMenuUtils.getNames(adapter.getItems());
		ArrayAdapter<String> alertDialogAdapter = new ArrayAdapter<String>(activity, layout, R.id.title, modifiableGpxFileNames) {

			@Override
			public int getItemViewType(int position) {
				return showCurrentGpx && position == 0 ? 1 : 0;
			}

			@Override
			public int getViewTypeCount() {
				return 2;
			}

			private GpxDataItem getDataItem(GPXInfo info) {
				return app.getGpxDbHelper().getItem(
						new File(app.getAppPath(IndexConstants.GPX_INDEX_DIR), info.getFileName()),
						gpxDataItemCallback);
			}

			@Override
			@NonNull
			public View getView(int position, View convertView, @NonNull ViewGroup parent) {
				// User super class to create the View
				View v = convertView;
				boolean checkLayout = getItemViewType(position) == 0;
				if (v == null) {
					v = View.inflate(new ContextThemeWrapper(activity, themeRes), layout, null);
				}

				ContextMenuItem item = adapter.getItem(position);
				GPXInfo info = gpxInfoList.get(position);
				boolean currentlyRecordingTrack = showCurrentGpx && position == 0;

				GPXTrackAnalysis analysis = null;
				if (currentlyRecordingTrack) {
					analysis = app.getSavingTrackHelper().getCurrentTrack().getTrackAnalysis(app);
				} else {
					GpxDataItem dataItem = getDataItem(info);
					if (dataItem != null) {
						analysis = dataItem.getAnalysis();
					}
				}
				updateGpxInfoView(v, item.getTitle(), info, analysis, app);

				if (item.getSelected() == null) {
					v.findViewById(R.id.check_item).setVisibility(View.GONE);
					v.findViewById(R.id.check_local_index).setVisibility(View.GONE);
				} else {
					if (checkLayout) {
						CheckBox ch = v.findViewById(R.id.check_local_index);
						ch.setVisibility(View.VISIBLE);
						v.findViewById(R.id.toggle_item).setVisibility(View.GONE);
						ch.setOnCheckedChangeListener(null);
						ch.setChecked(item.getSelected());
						ch.setOnCheckedChangeListener((buttonView, isChecked) -> {
							item.setSelected(isChecked);
						});
						UiUtilities.setupCompoundButton(ch, nightMode, PROFILE_DEPENDENT);
					} else {
						SwitchCompat ch = v.findViewById(R.id.toggle_item);
						ch.setVisibility(View.VISIBLE);
						v.findViewById(R.id.toggle_checkbox_item).setVisibility(View.GONE);
						ch.setOnCheckedChangeListener(null);
						ch.setChecked(item.getSelected());
						ch.setOnCheckedChangeListener((buttonView, isChecked) -> {
							item.setSelected(isChecked);
						});
						UiUtilities.setupCompoundButton(ch, nightMode, PROFILE_DEPENDENT);
					}
					v.findViewById(R.id.check_item).setVisibility(View.VISIBLE);
				}
				return v;
			}
		};

		OnClickListener onClickListener = (dialog, position) -> {
		};
		gpxDataItemCallback.setListAdapter(alertDialogAdapter);
		builder.setAdapter(alertDialogAdapter, onClickListener);
		if (multipleChoice) {
			builder.setTitle(R.string.show_gpx);
			builder.setPositiveButton(R.string.shared_string_ok, (dialog, which) -> {
				GPXFile currentGPX = null;
				//clear all previously selected files before adding new one
				if (app.getSelectedGpxHelper() != null) {
					app.getSelectedGpxHelper().clearAllGpxFilesToShow(false);
				}
				if (showCurrentGpx && adapter.getItem(0).getSelected()) {
					currentGPX = app.getSavingTrackHelper().getCurrentGpx();
				}
				List<String> selectedGpxNames = new ArrayList<>();
				for (int i = (showCurrentGpx ? 1 : 0); i < adapter.length(); i++) {
					if (adapter.getItem(i).getSelected()) {
						selectedGpxNames.add(gpxInfoList.get(i).getFileName());
					}
				}
				dialog.dismiss();
				loadGPXFileInDifferentThread(activity, callbackWithObject, dir, currentGPX,
						selectedGpxNames.toArray(new String[0]));
			});
			builder.setNegativeButton(R.string.shared_string_cancel, null);
			if (gpxInfoList.size() > 1 || !showCurrentGpx && gpxInfoList.size() > 0) {
				builder.setNeutralButton(R.string.gpx_add_track, null);
			}
		}

		AlertDialog dlg = builder.create();
		dlg.setCanceledOnTouchOutside(true);
		if (gpxInfoList.size() == 0 || showCurrentGpx && gpxInfoList.size() == 1) {
			View footerView = activity.getLayoutInflater().inflate(R.layout.no_gpx_files_list_footer, null);
			TextView descTextView = footerView.findViewById(R.id.descFolder);
			String descPrefix = app.getString(R.string.gpx_no_tracks_title_folder);
			SpannableString spannableDesc = new SpannableString(descPrefix + ": " + dir.getAbsolutePath());
			spannableDesc.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
					descPrefix.length() + 1, spannableDesc.length(), 0);
			descTextView.setText(spannableDesc);
			footerView.findViewById(R.id.button).setOnClickListener(v -> {
				addTrack(activity, alertDialogAdapter, adapter, gpxInfoList);
			});
			dlg.getListView().addFooterView(footerView, null, false);
		}
		dlg.getListView().setOnItemClickListener((parent, view, position, id) -> {
			if (multipleChoice) {
				ContextMenuItem item = adapter.getItem(position);
				item.setSelected(!item.getSelected());
				alertDialogAdapter.notifyDataSetInvalidated();
				if (position == 0 && showCurrentGpx && item.getSelected()) {
					OsmandMonitoringPlugin monitoringPlugin = PluginsHelper.getActivePlugin(OsmandMonitoringPlugin.class);
					if (monitoringPlugin == null) {
						AlertDialog.Builder confirm = new AlertDialog.Builder(new ContextThemeWrapper(activity, themeRes));
						confirm.setPositiveButton(R.string.shared_string_ok, (dialog, which) -> {
							Bundle params = new Bundle();
							params.putBoolean(PluginsFragment.OPEN_PLUGINS, true);
							MapActivity.launchMapActivityMoveToTop(activity, null, null, params);
						});
						confirm.setNegativeButton(R.string.shared_string_cancel, null);
						confirm.setMessage(activity.getString(R.string.enable_plugin_monitoring_services));
						confirm.show();
					} else if (!app.getSettings().SAVE_GLOBAL_TRACK_TO_GPX.get()) {
						monitoringPlugin.showTripRecordingDialog(activity);
					}
				}
			} else {
				dlg.dismiss();
				if (showCurrentGpx && position == 0) {
					callbackWithObject.processResult(null);
				} else {
					String fileName = gpxInfoList.get(position).getFileName();
					SelectedGpxFile selectedGpxFile =
							app.getSelectedGpxHelper().getSelectedFileByName(fileName);
					if (selectedGpxFile != null) {
						callbackWithObject.processResult(new GPXFile[] {selectedGpxFile.getGpxFile()});
					} else {
						loadGPXFileInDifferentThread(activity, callbackWithObject, dir, null, fileName);
					}
				}
			}
		});
		dlg.setOnShowListener(dialog -> {
			Button addTrackButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
			if (addTrackButton != null) {
				addTrackButton.setOnClickListener(v -> {
					addTrack(activity, alertDialogAdapter, adapter, gpxInfoList);
				});
			}
		});
		dlg.setOnDismissListener(dialog -> gpxDataItemCallback.setUpdateEnable(false));
		dlg.show();
		try {
			dlg.getListView().setFastScrollEnabled(true);
		} catch (Exception e) {
			// java.lang.ClassCastException: com.android.internal.widget.RoundCornerListAdapter
			// Unknown reason but on some devices fail
		}
		return dlg;
	}

	public static void updateGpxInfoView(@NonNull OsmandApplication app,
	                                     @NonNull View view,
	                                     @NonNull String itemTitle,
	                                     @Nullable Drawable iconDrawable,
	                                     @NonNull GPXInfo info) {
		GpxDataItem item = getDataItem(app, info, new GpxDataItemCallback() {
			@Override
			public void onGpxDataItemReady(GpxDataItem item) {
				updateGpxInfoView(app, view, itemTitle, iconDrawable, info, item);
			}
		});
		if (item != null) {
			updateGpxInfoView(app, view, itemTitle, iconDrawable, info, item);
		}
	}

	private static void updateGpxInfoView(@NonNull OsmandApplication app,
	                                      @NonNull View v,
	                                      @NonNull String itemTitle,
	                                      @Nullable Drawable iconDrawable,
	                                      @NonNull GPXInfo info,
	                                      @NonNull GpxDataItem dataItem) {
		updateGpxInfoView(v, itemTitle, info, dataItem.getAnalysis(), app);
		if (iconDrawable != null) {
			ImageView icon = v.findViewById(R.id.icon);
			icon.setImageDrawable(iconDrawable);
			icon.setVisibility(View.VISIBLE);
		}
	}

	public static void updateGpxInfoView(View v,
	                                     String itemTitle,
	                                     GPXInfo info,
	                                     GPXTrackAnalysis analysis,
	                                     OsmandApplication app) {
		TextView viewName = v.findViewById(R.id.name);
		viewName.setText(itemTitle.replace("/", " • ").trim());
		viewName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
		ImageView icon = v.findViewById(R.id.icon);
		icon.setVisibility(View.GONE);
		//icon.setImageDrawable(app.getIconsCache().getThemedIcon(R.drawable.ic_action_polygom_dark));

		boolean sectionRead = analysis == null;
		if (sectionRead) {
			v.findViewById(R.id.read_section).setVisibility(View.GONE);
			v.findViewById(R.id.unknown_section).setVisibility(View.VISIBLE);
			String date = "";
			String size = "";
			if (info.getFileSize() >= 0) {
				size = AndroidUtils.formatSize(v.getContext(), info.getFileSize());
			}
			DateFormat df = app.getResourceManager().getDateFormat();
			long fd = info.getLastModified();
			if (fd > 0) {
				date = (df.format(new Date(fd)));
			}
			TextView sizeText = v.findViewById(R.id.date_and_size_details);
			sizeText.setText(date + " \u2022 " + size);

		} else {
			v.findViewById(R.id.read_section).setVisibility(View.VISIBLE);
			v.findViewById(R.id.unknown_section).setVisibility(View.GONE);
			ImageView distanceI = v.findViewById(R.id.distance_icon);
			distanceI.setVisibility(View.VISIBLE);
			distanceI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_distance_16));
			ImageView pointsI = v.findViewById(R.id.points_icon);
			pointsI.setVisibility(View.VISIBLE);
			pointsI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_waypoint_16));
			ImageView timeI = v.findViewById(R.id.time_icon);
			timeI.setVisibility(View.VISIBLE);
			timeI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_time_16));
			TextView time = v.findViewById(R.id.time);
			TextView distance = v.findViewById(R.id.distance);
			TextView pointsCount = v.findViewById(R.id.points_count);
			pointsCount.setText(analysis.wptPoints + "");
			distance.setText(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));

			if (analysis.isTimeSpecified()) {
				time.setText(Algorithms.formatDuration((int) (analysis.timeSpan / 1000.0f + 0.5), app.accessibilityEnabled()) + "");
			} else {
				time.setText("");
			}
		}

		TextView descr = v.findViewById(R.id.description);
		if (descr != null) {
			descr.setVisibility(View.GONE);
		}

		View checkbox = v.findViewById(R.id.check_item);
		if (checkbox != null) {
			checkbox.setVisibility(View.GONE);
		}
	}

	private static GpxDataItem getDataItem(@NonNull OsmandApplication app,
	                                       @NonNull GPXInfo info,
	                                       @Nullable GpxDataItemCallback callback) {
		File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		String fileName = info.getFileName();
		File file = new File(dir, fileName);
		return app.getGpxDbHelper().getItem(file, callback);
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static void addTrack(Activity activity, ArrayAdapter<String> listAdapter,
	                             ContextMenuAdapter contextMenuAdapter, List<GPXInfo> allGpxFiles) {
		if (activity instanceof MapActivity) {
			MapActivity mapActivity = (MapActivity) activity;
			OnActivityResultListener listener = (resultCode, resultData) -> {
				if (resultCode != Activity.RESULT_OK || resultData == null) {
					return;
				}

				ImportHelper importHelper = mapActivity.getImportHelper();
				importHelper.setGpxImportCompleteListener(new ImportHelper.OnGpxImportCompleteListener() {
					@Override
					public void onSaveComplete(boolean success, GPXFile result) {
						if (success) {
							OsmandApplication app = (OsmandApplication) activity.getApplication();
							GpxSelectionParams params = GpxSelectionParams.newInstance()
									.showOnMap().syncGroup().selectedByUser().addToMarkers()
									.addToHistory().saveSelection();
							app.getSelectedGpxHelper().selectGpxFile(result, params);
							updateGpxDialogAfterImport(activity, listAdapter, contextMenuAdapter, allGpxFiles, result.path);
						}
					}
				});

				Uri uri = resultData.getData();
				importHelper.handleGpxImport(uri, null, false);
			};

			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.setType("*/*");
			try {
				mapActivity.startActivityForResult(intent, OPEN_GPX_DOCUMENT_REQUEST);
				mapActivity.registerActivityResultListener(new ActivityResultListener(OPEN_GPX_DOCUMENT_REQUEST, listener));
			} catch (ActivityNotFoundException e) {
				Toast.makeText(mapActivity, R.string.no_activity_for_intent, Toast.LENGTH_LONG).show();
			}
		}
	}

	private static void updateGpxDialogAfterImport(Activity activity,
	                                               ArrayAdapter<String> dialogAdapter,
	                                               ContextMenuAdapter adapter, List<GPXInfo> allGpxFiles,
	                                               String importedGpx) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();

		List<String> selectedGpxFiles = new ArrayList<>();
		selectedGpxFiles.add(importedGpx);
		for (int i = 0; i < allGpxFiles.size(); i++) {
			GPXInfo gpxInfo = allGpxFiles.get(i);
			ContextMenuItem menuItem = adapter.getItem(i);
			if (menuItem.getSelected()) {
				boolean isCurrentTrack =
						gpxInfo.getFileName().equals(app.getString(R.string.show_current_gpx_title));
				selectedGpxFiles.add(isCurrentTrack ? "" : gpxInfo.getFileName());
			}
		}
		allGpxFiles.clear();
		allGpxFiles.addAll(listGpxInfo(app, selectedGpxFiles, false));
		adapter.clear();
		fillGpxContextMenuAdapter(adapter, allGpxFiles, true);
		dialogAdapter.clear();
		dialogAdapter.addAll(CtxMenuUtils.getNames(adapter.getItems()));
		dialogAdapter.notifyDataSetInvalidated();
	}

	@NonNull
	public static List<String> getSelectedTrackPaths(OsmandApplication app) {
		List<String> trackNames = new ArrayList<>();
		for (SelectedGpxFile file : app.getSelectedGpxHelper().getSelectedGPXFiles()) {
			trackNames.add(file.getGpxFile().path);
		}
		return trackNames;
	}

	public static List<GPXInfo> getSortedGPXFilesInfoByDate(File dir, boolean absolutePath) {
		List<GPXInfo> list = new ArrayList<>();
		readGpxDirectory(dir, list, "", absolutePath);
		Collections.sort(list, (object1, object2) -> {
			long lhs = object1.getLastModified();
			long rhs = object2.getLastModified();
			return Long.compare(rhs, lhs);
		});
		return list;
	}

	@Nullable
	public static GPXInfo getGpxInfoByFileName(@NonNull OsmandApplication app, @NonNull String fileName) {
		File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		File file = new File(dir, fileName);
		if (file.exists() && file.getName().endsWith(GPX_FILE_EXT)) {
			return new GPXInfo(fileName, file);
		}
		return null;
	}

	@NonNull
	public static List<GPXInfo> getGPXFiles(@NonNull File dir, boolean absolutePath) {
		List<GPXInfo> gpxInfos = new ArrayList<>();
		readGpxDirectory(dir, gpxInfos, "", absolutePath);
		return gpxInfos;
	}

	@NonNull
	public static List<GPXInfo> getSortedGPXFilesInfo(File dir, List<String> selectedGpxList, boolean absolutePath) {
		List<GPXInfo> allGpxFiles = new ArrayList<>();
		readGpxDirectory(dir, allGpxFiles, "", absolutePath);
		if (selectedGpxList != null) {
			for (GPXInfo gpxInfo : allGpxFiles) {
				for (String selectedGpxName : selectedGpxList) {
					if (selectedGpxName.endsWith(gpxInfo.getFileName())) {
						gpxInfo.setSelected(true);
						break;
					}
				}
			}
		}
		Collections.sort(allGpxFiles, new Comparator<GPXInfo>() {
			@Override
			public int compare(GPXInfo i1, GPXInfo i2) {
				int res = i1.isSelected() == i2.isSelected() ? 0 : i1.isSelected() ? -1 : 1;
				if (res != 0) {
					return res;
				}

				String name1 = i1.getFileName();
				String name2 = i2.getFileName();
				int d1 = depth(name1);
				int d2 = depth(name2);
				if (d1 != d2) {
					return d1 - d2;
				}
				int lastSame = 0;
				for (int i = 0; i < name1.length() && i < name2.length(); i++) {
					if (name1.charAt(i) != name2.charAt(i)) {
						break;
					}
					if (name1.charAt(i) == '/') {
						lastSame = i + 1;
					}
				}

				boolean isDigitStarts1 = isLastSameStartsWithDigit(name1, lastSame);
				boolean isDigitStarts2 = isLastSameStartsWithDigit(name2, lastSame);
				res = isDigitStarts1 == isDigitStarts2 ? 0 : isDigitStarts1 ? -1 : 1;
				if (res != 0) {
					return res;
				}
				if (isDigitStarts1) {
					return -name1.compareToIgnoreCase(name2);
				}
				return name1.compareToIgnoreCase(name2);
			}

			private int depth(String name1) {
				int d = 0;
				for (int i = 0; i < name1.length(); i++) {
					if (name1.charAt(i) == '/') {
						d++;
					}
				}
				return d;
			}

			private boolean isLastSameStartsWithDigit(String name1, int lastSame) {
				if (name1.length() > lastSame) {
					return Character.isDigit(name1.charAt(lastSame));
				}

				return false;
			}
		});
		return allGpxFiles;
	}

	public static void readGpxDirectory(@Nullable File dir, @NonNull List<GPXInfo> list,
	                                    @NonNull String parent, boolean absolutePath) {
		if (dir != null && dir.canRead()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (file.isFile() && name.toLowerCase().endsWith(GPX_FILE_EXT)) {
						String fileName = absolutePath ? file.getAbsolutePath() : parent + name;
						list.add(new GPXInfo(fileName, file));
					} else if (file.isDirectory()) {
						readGpxDirectory(file, list, parent + name + "/", absolutePath);
					}
				}
			}
		}
	}

	public static void loadGPXFileInDifferentThread(Activity activity, CallbackWithObject<GPXFile[]> callback,
	                                                File dir, GPXFile currentFile, String... filename) {
		ProgressDialog dlg = ProgressDialog.show(activity, activity.getString(R.string.loading_smth, ""),
				activity.getString(R.string.loading_data));
		new Thread(() -> {
			GPXFile[] result = new GPXFile[filename.length + (currentFile == null ? 0 : 1)];
			int k = 0;
			StringBuilder builder = new StringBuilder();
			if (currentFile != null) {
				result[k++] = currentFile;
			}
			for (String name : filename) {
				File file = new File(dir, name);
				GPXFile gpxFile = GPXUtilities.loadGPXFile(file);
				if (gpxFile.error != null && !Algorithms.isEmpty(gpxFile.error.getMessage())) {
					builder.append(gpxFile.error.getMessage()).append("\n");
				} else {
					gpxFile.addGeneralTrack();
				}
				result[k++] = gpxFile;
			}
			dlg.dismiss();
			String warn = builder.toString();
			activity.runOnUiThread(() -> {
				if (warn.length() > 0) {
					Toast.makeText(activity, warn, Toast.LENGTH_LONG).show();
				} else {
					callback.processResult(result);
				}
			});
		}, "Loading gpx").start();
	}

	@NonNull
	public static GPXFile makeGpxFromRoute(RouteCalculationResult route, OsmandApplication app) {
		return makeGpxFromLocations(route.getRouteLocations(), app);
	}

	@NonNull
	public static GPXFile makeGpxFromLocations(List<Location> locations, OsmandApplication app) {
		double lastHeight = HEIGHT_UNDEFINED;
		double lastValidHeight = Double.NaN;
		GPXFile gpx = new GPXFile(Version.getFullVersion(app));
		if (locations != null) {
			Track track = new Track();
			TrkSegment seg = new TrkSegment();
			List<WptPt> pts = seg.points;
			for (Location l : locations) {
				WptPt point = new WptPt();
				point.lat = l.getLatitude();
				point.lon = l.getLongitude();
				if (l.hasAltitude()) {
					gpx.hasAltitude = true;
					float h = (float) l.getAltitude();
					point.ele = h;
					lastValidHeight = h;
					if (lastHeight == HEIGHT_UNDEFINED && pts.size() > 0) {
						for (WptPt pt : pts) {
							if (Double.isNaN(pt.ele)) {
								pt.ele = h;
							}
						}
					}
					lastHeight = h;
				} else {
					lastHeight = HEIGHT_UNDEFINED;
				}
				if (pts.size() == 0) {
					if (l.hasSpeed() && l.getSpeed() > 0) {
						point.speed = l.getSpeed();
					}
					point.time = System.currentTimeMillis();
				} else {
					GPXUtilities.WptPt prevPoint = pts.get(pts.size() - 1);
					if (l.hasSpeed() && l.getSpeed() > 0) {
						point.speed = l.getSpeed();
						double dist = MapUtils.getDistance(prevPoint.lat, prevPoint.lon, point.lat, point.lon);
						point.time = prevPoint.time + (long) (dist / point.speed * SECOND_IN_MILLIS);
					} else {
						point.time = prevPoint.time;
					}
				}
				pts.add(point);
			}
			if (!Double.isNaN(lastValidHeight) && lastHeight == HEIGHT_UNDEFINED) {
				for (ListIterator<WptPt> iterator = pts.listIterator(pts.size()); iterator.hasPrevious(); ) {
					WptPt point = iterator.previous();
					if (!Double.isNaN(point.ele)) {
						break;
					}
					point.ele = lastValidHeight;
				}
			}
			track.segments.add(seg);
			gpx.tracks.add(track);
		}
		return gpx;
	}

	public static GpxDisplayItem makeGpxDisplayItem(@NonNull OsmandApplication app, @NonNull GPXFile gpxFile,
	                                                @NonNull ChartPointLayer chartPointLayer) {
		GpxDisplayGroup group = null;
		if (!Algorithms.isEmpty(gpxFile.tracks)) {
			GpxDisplayHelper helper = app.getGpxDisplayHelper();
			String groupName = helper.getGroupName(gpxFile);
			group = helper.buildGpxDisplayGroup(gpxFile, 0, groupName);
		}
		if (group != null && group.getModifiableList().size() > 0) {
			GpxDisplayItem gpxItem = group.getModifiableList().get(0);
			if (gpxItem != null) {
				gpxItem.chartPointLayer = chartPointLayer;
			}
			return gpxItem;
		}
		return null;
	}

	public static void saveAndShareGpx(@NonNull Context context, @NonNull GPXFile gpxFile) {
		OsmandApplication app = (OsmandApplication) context.getApplicationContext();
		File tempDir = FileUtils.getTempDir(app);
		String fileName = Algorithms.getFileWithoutDirs(gpxFile.path);
		File file = new File(tempDir, fileName);
		SaveGpxListener listener = new SaveGpxListener() {
			@Override
			public void gpxSavingStarted() {

			}

			@Override
			public void gpxSavingFinished(Exception errorMessage) {
				if (errorMessage == null) {
					shareGpx(context, file);
				}
			}
		};
		new SaveGpxAsyncTask(file, gpxFile, listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static void saveAndShareCurrentGpx(@NonNull OsmandApplication app, @NonNull GPXFile gpxFile) {
		SaveGpxListener saveGpxListener = new SaveGpxListener() {
			@Override
			public void gpxSavingStarted() {

			}

			@Override
			public void gpxSavingFinished(Exception errorMessage) {
				if (errorMessage == null) {
					shareGpx(app, new File(gpxFile.path));
				}
			}
		};
		new SaveCurrentTrackTask(app, gpxFile, saveGpxListener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static void saveAndShareGpxWithAppearance(@NonNull Context context, @NonNull GPXFile gpxFile) {
		OsmandApplication app = (OsmandApplication) context.getApplicationContext();
		GpxDataItem dataItem = getDataItem(app, gpxFile);
		if (dataItem != null) {
			addAppearanceToGpx(gpxFile, dataItem);
			saveAndShareGpx(app, gpxFile);
		}
	}

	public static void saveGpx(GPXFile gpxFile, SaveGpxListener listener) {
		new SaveGpxAsyncTask(new File(gpxFile.path), gpxFile, listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static void saveAndOpenGpx(@NonNull MapActivity mapActivity,
	                                  @NonNull File file,
	                                  @NonNull GPXFile gpxFile,
	                                  @NonNull WptPt selectedPoint,
	                                  @Nullable GPXTrackAnalysis analyses,
	                                  @Nullable RouteKey routeKey) {
		new SaveGpxAsyncTask(file, gpxFile, new SaveGpxListener() {
			@Override
			public void gpxSavingStarted() {

			}

			@Override
			public void gpxSavingFinished(Exception errorMessage) {
				if (errorMessage == null) {
					OsmandApplication app = mapActivity.getMyApplication();
					GpxSelectionParams params = GpxSelectionParams.newInstance()
							.showOnMap().syncGroup().selectedByUser().addToMarkers()
							.addToHistory().saveSelection();
					SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().selectGpxFile(gpxFile, params);
					GPXTrackAnalysis trackAnalysis = analyses != null ? analyses : selectedGpxFile.getTrackAnalysis(app);
					SelectedGpxPoint selectedGpxPoint = new SelectedGpxPoint(selectedGpxFile, selectedPoint);
					Bundle bundle = new Bundle();
					bundle.putBoolean(TrackMenuFragment.ADJUST_MAP_POSITION, false);
					TrackMenuFragment.showInstance(mapActivity, selectedGpxFile, selectedGpxPoint,
							trackAnalysis, routeKey, bundle);
				} else {
					LOG.error(errorMessage);
				}
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private static GpxDataItem getDataItem(@NonNull OsmandApplication app, @NonNull GPXFile gpxFile) {
		GpxDataItemCallback callback = new GpxDataItemCallback() {
			@Override
			public void onGpxDataItemReady(GpxDataItem item) {
				addAppearanceToGpx(gpxFile, item);
				saveAndShareGpx(app, gpxFile);
			}
		};
		return app.getGpxDbHelper().getItem(new File(gpxFile.path), callback);
	}

	private static void addAppearanceToGpx(@NonNull GPXFile gpxFile, @NonNull GpxDataItem dataItem) {
		gpxFile.setShowArrows(dataItem.isShowArrows());
		gpxFile.setShowStartFinish(dataItem.isShowStartFinish());
		gpxFile.setSplitInterval(dataItem.getSplitInterval());
		gpxFile.setSplitType(GpxSplitType.getSplitTypeByTypeId(dataItem.getSplitType()).getTypeName());
		if (dataItem.getColor() != 0) {
			gpxFile.setColor(dataItem.getColor());
		}
		if (dataItem.getWidth() != null) {
			gpxFile.setWidth(dataItem.getWidth());
		}
		if (dataItem.getColoringType() != null) {
			gpxFile.setColoringType(dataItem.getColoringType());
		}
		GpsFilter.writeValidFilterValuesToExtensions(gpxFile.getExtensionsToWrite(), dataItem);
	}

	public static void shareGpx(@NonNull Context context, @NonNull File file) {
		Uri fileUri = AndroidUtils.getUriForFile(context, file);
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_STREAM, fileUri);
		intent.setType("application/gpx+xml");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		if (context instanceof OsmandApplication) {
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		Intent chooserIntent = Intent.createChooser(intent, context.getString(R.string.shared_string_share));
		AndroidUtils.startActivityIfSafe(context, chooserIntent);
	}

	@NonNull
	public static String getGpxFileRelativePath(@NonNull OsmandApplication app, @NonNull String fullPath) {
		String rootGpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR).getAbsolutePath() + '/';
		return fullPath.replace(rootGpxDir, "");
	}

	@Nullable
	public static WptPt getSegmentPointByTime(@NonNull TrkSegment segment, @NonNull GPXFile gpxFile,
	                                          float time, boolean preciseLocation, boolean joinSegments) {
		if (!segment.generalSegment || joinSegments) {
			return getSegmentPointByTime(segment, time, 0, preciseLocation);
		}

		long passedSegmentsTime = 0;
		for (Track track : gpxFile.tracks) {
			if (track.generalTrack) {
				continue;
			}

			for (TrkSegment seg : track.segments) {
				WptPt point = getSegmentPointByTime(seg, time, passedSegmentsTime, preciseLocation);
				if (point != null) {
					return point;
				}

				long segmentStartTime = Algorithms.isEmpty(seg.points) ? 0 : seg.points.get(0).time;
				long segmentEndTime = Algorithms.isEmpty(seg.points) ?
						0 : seg.points.get(seg.points.size() - 1).time;
				passedSegmentsTime += segmentEndTime - segmentStartTime;
			}
		}

		return null;
	}

	@Nullable
	private static WptPt getSegmentPointByTime(@NonNull TrkSegment segment, float timeToPoint,
	                                           long passedSegmentsTime, boolean preciseLocation) {
		WptPt previousPoint = null;
		long segmentStartTime = segment.points.get(0).time;
		for (WptPt currentPoint : segment.points) {
			long totalPassedTime = passedSegmentsTime + currentPoint.time - segmentStartTime;
			if (totalPassedTime >= timeToPoint) {
				return preciseLocation && previousPoint != null
						? getIntermediatePointByTime(totalPassedTime, timeToPoint, previousPoint, currentPoint)
						: currentPoint;
			}
			previousPoint = currentPoint;
		}
		return null;
	}

	@NonNull
	private static WptPt getIntermediatePointByTime(double passedTime, double timeToPoint,
	                                                WptPt prevPoint, WptPt currPoint) {
		double percent = 1 - (passedTime - timeToPoint) / (currPoint.time - prevPoint.time);
		double dLat = (currPoint.lat - prevPoint.lat) * percent;
		double dLon = (currPoint.lon - prevPoint.lon) * percent;
		WptPt intermediatePoint = new WptPt();
		intermediatePoint.lat = prevPoint.lat + dLat;
		intermediatePoint.lon = prevPoint.lon + dLon;
		return intermediatePoint;
	}

	@Nullable
	public static WptPt getSegmentPointByDistance(@NonNull TrkSegment segment, @NonNull GPXFile gpxFile,
	                                              float distanceToPoint, boolean preciseLocation,
	                                              boolean joinSegments) {
		double passedDistance = 0;
		if (!segment.generalSegment || joinSegments) {
			WptPt prevPoint = null;
			for (int i = 0; i < segment.points.size(); i++) {
				WptPt currPoint = segment.points.get(i);
				if (prevPoint != null) {
					passedDistance += MapUtils.getDistance(prevPoint.lat, prevPoint.lon, currPoint.lat, currPoint.lon);
				}
				if (currPoint.distance >= distanceToPoint || Math.abs(passedDistance - distanceToPoint) < 0.1) {
					return preciseLocation && prevPoint != null && currPoint.distance >= distanceToPoint
							? getIntermediatePointByDistance(passedDistance, distanceToPoint, currPoint, prevPoint)
							: currPoint;
				}
				prevPoint = currPoint;
			}
		}

		passedDistance = 0;
		double passedSegmentsPointsDistance = 0;
		WptPt prevPoint = null;
		for (Track track : gpxFile.tracks) {
			if (track.generalTrack) {
				continue;
			}
			for (TrkSegment seg : track.segments) {
				if (Algorithms.isEmpty(seg.points)) {
					continue;
				}
				for (WptPt currPoint : seg.points) {
					if (prevPoint != null) {
						passedDistance += MapUtils.getDistance(prevPoint.lat, prevPoint.lon,
								currPoint.lat, currPoint.lon);
					}
					if (passedSegmentsPointsDistance + currPoint.distance >= distanceToPoint
							|| Math.abs(passedDistance - distanceToPoint) < 0.1) {
						return preciseLocation && prevPoint != null
								&& currPoint.distance + passedSegmentsPointsDistance >= distanceToPoint
								? getIntermediatePointByDistance(passedDistance, distanceToPoint, currPoint, prevPoint)
								: currPoint;
					}
					prevPoint = currPoint;
				}
				prevPoint = null;
				passedSegmentsPointsDistance += seg.points.get(seg.points.size() - 1).distance;
			}
		}
		return null;
	}

	@NonNull
	private static WptPt getIntermediatePointByDistance(double passedDistance, double distanceToPoint,
	                                                    WptPt currPoint, WptPt prevPoint) {
		double percent = 1 - (passedDistance - distanceToPoint) / (currPoint.distance - prevPoint.distance);
		double dLat = (currPoint.lat - prevPoint.lat) * percent;
		double dLon = (currPoint.lon - prevPoint.lon) * percent;
		WptPt intermediatePoint = new WptPt();
		intermediatePoint.lat = prevPoint.lat + dLat;
		intermediatePoint.lon = prevPoint.lon + dLon;
		return intermediatePoint;
	}
}
