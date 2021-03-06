package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jaredrummler.android.shell.Shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.AppDetailsItem;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.activities.AppDetailsActivity;
import io.github.muntashirakon.AppManager.activities.AppInfoActivity;
import io.github.muntashirakon.AppManager.compontents.ComponentsApplier;
import io.github.muntashirakon.AppManager.compontents.ComponentsApplier.ComponentType;
import io.github.muntashirakon.AppManager.utils.LauncherIconCreator;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;


public class AppDetailsFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    private static final String NEEDED_PROPERTY_INT = "neededProperty";

    @IntDef(value = {
            NONE,
            ACTIVITIES,
            SERVICES,
            RECEIVERS,
            PROVIDERS,
            USES_PERMISSIONS,
            PERMISSIONS,
            FEATURES,
            CONFIGURATION,
            SIGNATURES,
            SHARED_LIBRARY_FILES
    })
    public @interface Property {}
    public static final int NONE = -1;
    public static final int ACTIVITIES = 0;
    public static final int SERVICES = 1;
    public static final int RECEIVERS = 2;
    public static final int PROVIDERS = 3;
    public static final int USES_PERMISSIONS = 4;
    public static final int PERMISSIONS = 5;
    public static final int FEATURES = 6;
    public static final int CONFIGURATION = 7;
    public static final int SIGNATURES = 8;
    public static final int SHARED_LIBRARY_FILES = 9;

    private @Property int neededProperty;

    private LayoutInflater mLayoutInflater;
    private String mPackageName;
    private PackageManager mPackageManager;
    private PackageInfo mPackageInfo;
    private Activity mActivity;
    private ActivitiesListAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;
    private ComponentsApplier mComponentsApplier;
    private MenuItem blockingToggler;

    private Tuple<String, Integer>[] permissionsWithFlags;
    private boolean bFi;

    private int mColorGrey1;
    private int mColorGrey2;
    private int mColorRed;

    // Load from saved instance if empty constructor is called.
    private boolean isEmptyFragmentConstructCalled = false;
    public AppDetailsFragment() {
        isEmptyFragmentConstructCalled = true;
    }

    public AppDetailsFragment(@Property int neededProperty) {
        this.neededProperty = neededProperty;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(NEEDED_PROPERTY_INT, neededProperty);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (isEmptyFragmentConstructCalled && savedInstanceState != null){
            neededProperty = savedInstanceState.getInt(NEEDED_PROPERTY_INT);
        }
        try {
            mPackageName = Objects.requireNonNull(getActivity()).getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        } catch (NullPointerException e) {
            return;
        }
        mPackageManager = getActivity().getPackageManager();
        mLayoutInflater = getLayoutInflater();
        mActivity = getActivity();
        if (mActivity != null) {
            mColorGrey1 = Color.TRANSPARENT;
            mColorGrey2 = mActivity.getResources().getColor(R.color.SEMI_TRANSPARENT);
            mColorRed = mActivity.getResources().getColor(R.color.red);
        }
        getPackageInfo(mPackageName);

        mComponentsApplier = ComponentsApplier.getInstance(mActivity, mPackageName);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pager_app_details, container, false);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(mActivity, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(mActivity, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);
        ListView listView = view.findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(getNeededString(neededProperty));
        listView.setEmptyView(emptyView);
        mAdapter = new ActivitiesListAdapter();
        listView.setAdapter(mAdapter);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> listView.canScrollVertically(-1));
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mComponentsApplier.applyRules(true);
//        Toast.makeText(mActivity, "The current configurations has been applied!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_app_details_action, menu);
        blockingToggler = menu.findItem(R.id.action_toggle_blocking);
        if (mComponentsApplier.isRulesApplied()) {
            blockingToggler.setTitle(R.string.menu_disable_blocking);
        } else {
            blockingToggler.setTitle(R.string.menu_enable_blocking);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh_details:
                refreshDetails();
                return true;
            case R.id.action_toggle_blocking:
                boolean isRulesApplied = mComponentsApplier.isRulesApplied();
                mComponentsApplier.applyRules(!isRulesApplied);
                if (mComponentsApplier.isRulesApplied()) {
                    blockingToggler.setTitle(R.string.menu_disable_blocking);
                } else {
                    blockingToggler.setTitle(R.string.menu_enable_blocking);
                }
                refreshDetails();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null) mAdapter.getFilter().filter(newText);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void refreshDetails() {
        if (mAdapter != null){
            getPackageInfo(mPackageName);
            mAdapter.reset();
        }
    }

    /**
     * Get package info.
     *
     * @param packageName Package name (e.g. com.android.wallpaper)
     */
    private void getPackageInfo(String packageName) {
        try {
            int apiCompatFlags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                apiCompatFlags = PackageManager.GET_SIGNING_CERTIFICATES;
            else apiCompatFlags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                apiCompatFlags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            else apiCompatFlags |= PackageManager.GET_DISABLED_COMPONENTS;

            mPackageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | apiCompatFlags | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);

            if (mPackageInfo == null) return;

            switch (neededProperty){
                case SERVICES:
                    if (mPackageInfo.services != null) {
                        Arrays.sort(mPackageInfo.services, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case RECEIVERS:
                    if (mPackageInfo.receivers != null) {
                        Arrays.sort(mPackageInfo.receivers, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case PROVIDERS:
                    if (mPackageInfo.providers != null) {
                        Arrays.sort(mPackageInfo.providers, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case USES_PERMISSIONS:  // Requested Permissions
                    if (mPackageInfo.requestedPermissions == null) permissionsWithFlags = null;
                    else {
                        //noinspection unchecked
                        permissionsWithFlags = new Tuple[mPackageInfo.requestedPermissions.length];
                        for (int i = 0; i < mPackageInfo.requestedPermissions.length; ++i) {
                            permissionsWithFlags[i] = new Tuple<>(mPackageInfo.requestedPermissions[i],
                                    mPackageInfo.requestedPermissionsFlags[i]);
                        }
                    }
                    break;
                case PERMISSIONS:
                    if (mPackageInfo.permissions != null) {
                        Arrays.sort(mPackageInfo.permissions, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case FEATURES:  // Requested Features
                    if (mPackageInfo.reqFeatures != null) {
                        try {
                            Arrays.sort(mPackageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        } catch (NullPointerException e) {
                            for (FeatureInfo fi : mPackageInfo.reqFeatures) {
                                if (fi.name == null) fi.name = "_MAJOR";
                                bFi = true;
                            }
                            Arrays.sort(mPackageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        }
                    }
                    break;
                case CONFIGURATION:
                case SIGNATURES:
                case SHARED_LIBRARY_FILES:
                    break;
                case ACTIVITIES:
                case NONE:
                default:
                    if (mPackageInfo.activities != null) {
                        Arrays.sort(mPackageInfo.activities, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            mActivity.finish();
        }
    }

    /**
     * Return corresponding section's array
     */
    private @NonNull List<AppDetailsItem> getNeededList(@Property int index) {
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        switch (index) {
            case SERVICES:
                if (mPackageInfo.services != null) {
                    for(ServiceInfo serviceInfo: mPackageInfo.services) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(serviceInfo);
                        appDetailsItem.name = serviceInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case RECEIVERS:
                if (mPackageInfo.receivers != null) {
                    for(ActivityInfo activityInfo: mPackageInfo.receivers) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(activityInfo);
                        appDetailsItem.name = activityInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case PROVIDERS:
                if (mPackageInfo.providers != null) {
                    for(ProviderInfo providerInfo: mPackageInfo.providers) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(providerInfo);
                        appDetailsItem.name = providerInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case USES_PERMISSIONS:
                if (permissionsWithFlags != null) {
                    for(Tuple<String, Integer> permissionWithFlags: permissionsWithFlags) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(permissionWithFlags);
                        appDetailsItem.name = permissionWithFlags.getFirst();
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case PERMISSIONS:
                if (mPackageInfo.permissions != null) {
                    for(PermissionInfo permissionInfo: mPackageInfo.permissions) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(permissionInfo);
                        appDetailsItem.name = permissionInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case FEATURES:
                if (mPackageInfo.reqFeatures != null) {
                    for(FeatureInfo featureInfo: mPackageInfo.reqFeatures) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(featureInfo);
                        appDetailsItem.name = featureInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case CONFIGURATION:
                if (mPackageInfo.configPreferences != null) {
                    for(ConfigurationInfo configurationInfo: mPackageInfo.configPreferences) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(configurationInfo);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case SIGNATURES:
                Signature[] signatures;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SigningInfo signingInfo = mPackageInfo.signingInfo;
                    signatures = signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                            : signingInfo.getSigningCertificateHistory();
                } else {
                    signatures = mPackageInfo.signatures;
                }
                if (signatures != null) {
                    for(Signature signature: signatures) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(signature);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case SHARED_LIBRARY_FILES:
                if (mPackageInfo.applicationInfo.sharedLibraryFiles != null) {
                    for(String sharedLibrary: mPackageInfo.applicationInfo.sharedLibraryFiles) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(sharedLibrary);
                        appDetailsItem.name = sharedLibrary;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case ACTIVITIES:
                if (mPackageInfo.activities != null) {
                    for(ActivityInfo activityInfo: mPackageInfo.activities) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(activityInfo);
                        appDetailsItem.name = activityInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case NONE:
            default:
        }
        return appDetailsItems;
    }

    /**
     * Return corresponding section's array
     */
    private int getNeededString(@Property int index) {
        switch (index) {
            case SERVICES: return R.string.no_service;
            case RECEIVERS: return R.string.no_receivers;
            case PROVIDERS: return R.string.no_providers;
            case USES_PERMISSIONS:
            case PERMISSIONS: return R.string.require_no_permission;
            case FEATURES: return R.string.no_feature;
            case CONFIGURATION: return R.string.no_configurations;
            case SIGNATURES: return R.string.no_signatures;
            case SHARED_LIBRARY_FILES: return R.string.no_shared_libs;
            case ACTIVITIES:
            case NONE:
            default: return R.string.no_activities;
        }
    }

    private class ActivitiesListAdapter extends BaseAdapter implements Filterable {
        private List<AppDetailsItem> mAdapterList;
        private List<AppDetailsItem> mDefaultList;
        private @Property int requestedProperty;
        private Filter mFilter;
        private String mConstraint;

        ActivitiesListAdapter() {
            reset();
        }

        void reset() {
            requestedProperty = neededProperty;
            mAdapterList = getNeededList(requestedProperty);
            mDefaultList = mAdapterList;
            notifyDataSetChanged();
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase();
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<AppDetailsItem> list = new ArrayList<>(mDefaultList.size());
                        for (AppDetailsItem item : mDefaultList) {
                            if (item.name.toLowerCase().contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<AppDetailsItem>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder {
            @Property int currentViewType = NONE;
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            TextView textView6;
            ImageView imageView;
            ImageButton blockBtn;
            Button createBtn;
            Button editBtn;
            Button launchBtn;
            Switch toggleSwitch;
        }

        @Override
        public int getCount() {
            return mAdapterList.size();
        }

        @Override
        public Object getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            switch (requestedProperty) {
                case SERVICES:
                    return getServicesView(parent, convertView, position);
                case RECEIVERS:
                    return getReceiverView(parent, convertView, position);
                case PROVIDERS:
                    return getProviderView(parent, convertView, position);
                case USES_PERMISSIONS:
                    return getUsesPermissionsView(parent, convertView, position);
                case PERMISSIONS:
                    return getPermissionsView(parent, convertView, position);
                case FEATURES:
                    return getFeaturesView(parent, convertView, position);
                case CONFIGURATION:
                    return getConfigurationView(parent, convertView, position);
                case SIGNATURES:
                    return getSignatureView(convertView, position);
                case SHARED_LIBRARY_FILES:
                    return getSharedLibsView(convertView, position);
                case ACTIVITIES:
                case NONE:
                default:
                    return getActivityView(parent, convertView, position);
            }
        }

        /**
         * See below checkIfConvertViewMatch method.
         * Bored view inflation / creation.
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getActivityView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, ACTIVITIES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = ACTIVITIES;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = convertView.findViewById(R.id.softInput);
                viewHolder.launchBtn = convertView.findViewById(R.id.launch);
                viewHolder.blockBtn  = convertView.findViewById(R.id.block_component);
                viewHolder.createBtn = convertView.findViewById(R.id.create_shortcut_btn);
                viewHolder.editBtn   = convertView.findViewById(R.id.edit_shortcut_btn);
                convertView.findViewById(R.id.label).setVisibility(View.GONE);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = (ActivityInfo) mAdapterList.get(index).vanillaItem;
            final String activityName = activityInfo.name;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (mComponentsApplier.hasComponent(activityName)) {
                convertView.setBackgroundColor(mColorRed);
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Name
            if (mConstraint != null && activityName.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView2.setText(Utils.getHighlightedText(activityName, mConstraint, mColorRed));
            } else {
                viewHolder.textView2.setText(activityName.startsWith(mPackageName) ?
                        activityName.replaceFirst(mPackageName, "") : activityName);
            }
            // Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));
            // TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);
            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode)
                    + " | " + getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));
            // Orientation
            viewHolder.textView5.setText(Utils.getActivitiesFlagsString(activityInfo.flags));
            // SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode)
                    + " | " + (activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission));
            // Label
            Button launch = viewHolder.launchBtn;
            launch.setText(activityInfo.loadLabel(mPackageManager));
            boolean isExported = activityInfo.exported;
            launch.setEnabled(isExported);
            if (isExported) {
                launch.setOnClickListener(view -> {
                    Intent intent = new Intent();
                    intent.setClassName(mPackageName, activityName);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        if (getActivity() != null)
                            getActivity().recreate();
                        Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                    }
                });
                viewHolder.createBtn.setOnClickListener(view -> {
                    String iconResourceName = null;
                    try {
                        ComponentName activity = new ComponentName(activityInfo.packageName, activityName);
                        iconResourceName = mPackageManager.getResourcesForActivity(activity)
                                .getResourceName(activityInfo.getIconResource());
                    } catch (PackageManager.NameNotFoundException e) {
                        Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                    }
                    LauncherIconCreator.createLauncherIcon(getActivity(), activityInfo,
                            (String) activityInfo.loadLabel(mPackageManager),
                            activityInfo.loadIcon(mPackageManager), iconResourceName);
                });
                viewHolder.editBtn.setOnClickListener(view -> {
                    if (getFragmentManager() != null) {
                        DialogFragment dialog = new EditShortcutDialogFragment();
                        Bundle args = new Bundle();
                        args.putParcelable(EditShortcutDialogFragment.ARG_ACTIVITY_INFO, activityInfo);
                        dialog.setArguments(args);
                        dialog.show(getFragmentManager(), EditShortcutDialogFragment.TAG);
                    }
                });
                viewHolder.createBtn.setVisibility(View.VISIBLE);
                viewHolder.editBtn.setVisibility(View.VISIBLE);
            } else {
                viewHolder.createBtn.setVisibility(View.GONE);
                viewHolder.editBtn.setVisibility(View.GONE);
            }

            viewHolder.blockBtn.setOnClickListener(v -> {
                if (mComponentsApplier.hasComponent(activityName)) { // Remove from the list
                    mComponentsApplier.removeComponent(activityName);
                } else { // Add to the list
                    mComponentsApplier.addComponent(activityName, ComponentType.ACTIVITY);
                }
                try {
                    mComponentsApplier.saveDisabledComponents();
                    refreshDetails();
                } catch (IOException e) {
                    Toast.makeText(mActivity, "Failed to save component details to the local disk!", Toast.LENGTH_LONG).show();
                }
            });
            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getServicesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, SERVICES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = SERVICES;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.orientation);
                viewHolder.blockBtn  = convertView.findViewById(R.id.block_component);
                convertView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                convertView.findViewById(R.id.launchMode).setVisibility(View.GONE);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ServiceInfo serviceInfo = (ServiceInfo) mAdapterList.get(index).vanillaItem;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (mComponentsApplier.hasComponent(serviceInfo.name)) {
                convertView.setBackgroundColor(mColorRed);
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }

            // Label
            viewHolder.textView1.setText(serviceInfo.loadLabel(mPackageManager));

            // Name
            if (mConstraint != null && serviceInfo.name.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView2.setText(Utils.getHighlightedText(serviceInfo.name, mConstraint, mColorRed));
            } else {
                viewHolder.textView2.setText(serviceInfo.name.startsWith(mPackageName) ?
                        serviceInfo.name.replaceFirst(mPackageName, "") : serviceInfo.name);
            }

            // Icon
            viewHolder.imageView.setImageDrawable(serviceInfo.loadIcon(mPackageManager));

            // Flags and 1Permission
            viewHolder.textView3.setText(Utils.getServiceFlagsString(serviceInfo.flags)
                    + (serviceInfo.permission != null ? "\n" + serviceInfo.permission : "\n"));

            viewHolder.blockBtn.setOnClickListener(v -> {
                if (mComponentsApplier.hasComponent(serviceInfo.name)) { // Remove from the list
                    mComponentsApplier.removeComponent(serviceInfo.name);
                } else { // Add to the list
                    mComponentsApplier.addComponent(serviceInfo.name, ComponentType.SERVICE);
                }
                try {
                    mComponentsApplier.saveDisabledComponents();
                    refreshDetails();
                } catch (IOException e) {
                    Toast.makeText(mActivity, "Failed to save component details to the local disk!", Toast.LENGTH_LONG).show();
                }
            });

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getReceiverView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, RECEIVERS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = RECEIVERS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = convertView.findViewById(R.id.softInput);
                viewHolder.blockBtn  = convertView.findViewById(R.id.block_component);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = (ActivityInfo) mAdapterList.get(index).vanillaItem;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (mComponentsApplier.hasComponent(activityInfo.name)) {
                convertView.setBackgroundColor(mColorRed);
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }

            // Label
            viewHolder.textView1.setText(activityInfo.loadLabel(mPackageManager));

            // Name
            if (mConstraint != null && activityInfo.name.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView2.setText(Utils.getHighlightedText(activityInfo.name, mConstraint, mColorRed));
            } else {
                viewHolder.textView2.setText(activityInfo.name.startsWith(mPackageName) ?
                        activityInfo.name.replaceFirst(mPackageName, "")
                        : activityInfo.name);
            }
            // Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));

            // TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);

            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode)
                    + " | " + getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));

            // Orientation
            viewHolder.textView5.setText(activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission);

            // SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode));

            viewHolder.blockBtn.setOnClickListener(v -> {
                if (mComponentsApplier.hasComponent(activityInfo.name)) { // Remove from the list
                    mComponentsApplier.removeComponent(activityInfo.name);
                } else { // Add to the list
                    mComponentsApplier.addComponent(activityInfo.name, ComponentType.RECEIVER);
                }
                try {
                    mComponentsApplier.saveDisabledComponents();
                    refreshDetails();
                } catch (IOException e) {
                    Toast.makeText(mActivity, "Failed to save component details to the local disk!", Toast.LENGTH_LONG).show();
                }
            });
            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getProviderView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, PROVIDERS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PROVIDERS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView4 = convertView.findViewById(R.id.orientation);
                viewHolder.textView5 = convertView.findViewById(R.id.softInput);
                viewHolder.textView6 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.blockBtn  = convertView.findViewById(R.id.block_component);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ProviderInfo providerInfo = (ProviderInfo) mAdapterList.get(index).vanillaItem;
            final String providerName = providerInfo.name;
            // Set background color
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (mComponentsApplier.hasComponent(providerName)) {
                convertView.setBackgroundColor(mColorRed);
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                viewHolder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Label
            viewHolder.textView1.setText(providerInfo.loadLabel(mPackageManager));
            // Icon
            viewHolder.imageView.setImageDrawable(providerInfo.loadIcon(mPackageManager));
            // Uri permission
            viewHolder.textView3.setText(getString(R.string.grant_uri_permission) + ": " + providerInfo.grantUriPermissions);
            // Path permissions
            PathPermission[] pathPermissions = providerInfo.pathPermissions;
            String finalString;
            if (pathPermissions != null) {
                StringBuilder builder = new StringBuilder();
                String read = getString(R.string.read);
                String write = getString(R.string.write);
                for (PathPermission permission : pathPermissions) {
                    builder.append(read).append(": ").append(permission.getReadPermission());
                    builder.append("/");
                    builder.append(write).append(": ").append(permission.getWritePermission());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString = builder.toString();
            } else
                finalString = "null";
            viewHolder.textView4.setText(getString(R.string.path_permissions) + ": " + finalString);//+"\n"+providerInfo.readPermission +"\n"+providerInfo.writePermission);
            // Pattern matchers
            PatternMatcher[] patternMatchers = providerInfo.uriPermissionPatterns;
            String finalString1;
            if (patternMatchers != null) {
                StringBuilder builder = new StringBuilder();
                for (PatternMatcher patternMatcher : patternMatchers) {
                    builder.append(patternMatcher.toString());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString1 = builder.toString();
            } else
                finalString1 = "null";
            viewHolder.textView5.setText(getString(R.string.patterns_allowed) + ": " + finalString1);
            // Authority
            viewHolder.textView6.setText(getString(R.string.authority) + ": " + providerInfo.authority);
            // Name
            if (mConstraint != null && providerName.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView2.setText(Utils.getHighlightedText(providerName, mConstraint, mColorRed));
            } else {
                viewHolder.textView2.setText(providerName.startsWith(mPackageName) ?
                        providerName.replaceFirst(mPackageName, "") : providerName);
            }
            // Blocking
            viewHolder.blockBtn.setOnClickListener(v -> {
                if (mComponentsApplier.hasComponent(providerName)) { // Remove from the list
                    mComponentsApplier.removeComponent(providerName);
                } else { // Add to the list
                    mComponentsApplier.addComponent(providerName, ComponentType.PROVIDER);
                }
                try {
                    mComponentsApplier.saveDisabledComponents();
                    refreshDetails();
                } catch (IOException e) {
                    Toast.makeText(mActivity, "Failed to save component details to the local disk!", Toast.LENGTH_LONG).show();
                }
            });
            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getUsesPermissionsView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, USES_PERMISSIONS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_perm, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = USES_PERMISSIONS;
                viewHolder.textView1 = convertView.findViewById(R.id.perm_name);
                viewHolder.textView2 = convertView.findViewById(R.id.perm_description);
                viewHolder.textView3 = convertView.findViewById(R.id.perm_protection_level);
                viewHolder.textView4 = convertView.findViewById(R.id.perm_package_name);
                viewHolder.textView5 = convertView.findViewById(R.id.perm_group);
                viewHolder.toggleSwitch = convertView.findViewById(R.id.perm_toggle_btn);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            @SuppressWarnings("unchecked")
            Tuple<String, Integer> permTuple = (Tuple<String, Integer>) mAdapterList.get(index).vanillaItem;
            final String permName = permTuple.getFirst();
            final int permFlags = permTuple.getSecond();
            PermissionInfo permissionInfo = null;
            try {
                permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException ignore) {}

            // Set permission name
            if (mConstraint != null && permName.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView1.setText(Utils.getHighlightedText(permName, mConstraint, mColorRed));
            } else {
                viewHolder.textView1.setText(permName);
            }
            // Set others
            if (permissionInfo != null) {
                // Description
                CharSequence description = permissionInfo.loadDescription(mPackageManager);
                if (description != null) {
                    viewHolder.textView2.setVisibility(View.VISIBLE);
                    viewHolder.textView2.setText(description);
                } else viewHolder.textView2.setVisibility(View.GONE);
                // Protection level
                String protectionLevel = Utils.getProtectionLevelString(permissionInfo.protectionLevel);
                viewHolder.textView3.setText("\u2691 " + protectionLevel);
                if (protectionLevel.contains("dangerous"))
                    convertView.setBackgroundColor(mActivity.getResources().getColor(R.color.red));
                // Set package name
                if (permissionInfo.packageName != null) {
                    viewHolder.textView4.setVisibility(View.VISIBLE);
                    viewHolder.textView4.setText(String.format("%s: %s",
                            mActivity.getString(R.string.package_name), permissionInfo.packageName));
                } else viewHolder.textView4.setVisibility(View.GONE);
                // Set group name
                if (permissionInfo.group != null) {
                    viewHolder.textView5.setVisibility(View.VISIBLE);
                    viewHolder.textView5.setText(String.format("%s: %s",
                            mActivity.getString(R.string.group), permissionInfo.group));
                } else viewHolder.textView5.setVisibility(View.GONE);
                // Permission Switch
                if (protectionLevel.contains("dangerous")) {
                    viewHolder.toggleSwitch.setVisibility(View.VISIBLE);
                    if ((permFlags & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        // Permission granted
                        viewHolder.toggleSwitch.setChecked(true);
                    } else {
                        viewHolder.toggleSwitch.setChecked(false);
                    }
                    // FIXME: Remove this switch if root is not available
                    viewHolder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            // Enable permission
                            if (!Shell.SU.run(String.format("pm grant %s %s", mPackageName, permName)).isSuccessful()) {
                                Toast.makeText(mActivity, "Failed to grant permission.", Toast.LENGTH_LONG).show();
                                viewHolder.toggleSwitch.setChecked(false);
                            }
                        } else {
                            // Disable permission
                            if (!Shell.SU.run(String.format("pm revoke %s %s", mPackageName, permName)).isSuccessful()) {
                                Toast.makeText(mActivity, "Failed to revoke permission.", Toast.LENGTH_LONG).show();
                                viewHolder.toggleSwitch.setChecked(true);
                            }
                        }
                    });
                } else viewHolder.toggleSwitch.setVisibility(View.GONE);
            } else {
                viewHolder.textView2.setVisibility(View.GONE);
                viewHolder.textView3.setVisibility(View.GONE);
                viewHolder.textView4.setVisibility(View.GONE);
                viewHolder.textView5.setVisibility(View.GONE);
                viewHolder.toggleSwitch.setVisibility(View.GONE);
            }
            return convertView;
        }

        @NonNull
        private View getSharedLibsView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(mActivity);
            }

            TextView textView = (TextView) convertView;
            textView.setTextIsSelectable(true);
            textView.setText((String) mAdapterList.get(index).vanillaItem);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_very_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, small_size, medium_size, small_size);

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getPermissionsView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, PERMISSIONS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PERMISSIONS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.orientation);
                viewHolder.textView5 = convertView.findViewById(R.id.launchMode);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.block_component).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final PermissionInfo permissionInfo = (PermissionInfo) mAdapterList.get(index).vanillaItem;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Label
            viewHolder.textView1.setText(permissionInfo.loadLabel(mPackageManager));

            // Name
            if (mConstraint != null && permissionInfo.name.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                viewHolder.textView2.setText(Utils.getHighlightedText(permissionInfo.name, mConstraint, mColorRed));
            } else {
                viewHolder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                        permissionInfo.name.replaceFirst(mPackageName, "") : permissionInfo.name);
            }

            // Icon
            viewHolder.imageView.setImageDrawable(permissionInfo.loadIcon(mPackageManager));

            // Description
            viewHolder.textView3.setText(permissionInfo.loadDescription(mPackageManager));

            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.group) + ": " + permissionInfo.group
                    + permAppOp(permissionInfo.name));

            // Protection level
            viewHolder.textView5.setText(getString(R.string.protection_level) + ": " + Utils.getProtectionLevelString(permissionInfo.protectionLevel));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getFeaturesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, FEATURES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_secondary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = FEATURES;
                viewHolder.textView1 = convertView.findViewById(R.id.name);
                viewHolder.textView2 = convertView.findViewById(R.id.flags);
                viewHolder.textView3 = convertView.findViewById(R.id.gles_ver);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final FeatureInfo featureInfo = (FeatureInfo) mAdapterList.get(index).vanillaItem;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Name
            viewHolder.textView1.setText(featureInfo.name);

            // Flags
            viewHolder.textView2.setText(getString(R.string.flags) + ": " + Utils.getFeatureFlagsString(featureInfo.flags)
                    + (Build.VERSION.SDK_INT >= 24 && featureInfo.version != 0 ? " | minV%:" + featureInfo.version : ""));

            // GLES ver
            viewHolder.textView3.setText(getString(R.string.gles_ver) + ": " + (bFi && !featureInfo.name.equals("_MAJOR") ? "_" : Utils.getOpenGL(featureInfo.reqGlEsVersion)));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getConfigurationView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, CONFIGURATION)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_tertiary, viewGroup, false);
                viewHolder = new ViewHolder();
                viewHolder.currentViewType = CONFIGURATION;
                viewHolder.textView1 = convertView.findViewById(R.id.reqgles);
                viewHolder.textView2 = convertView.findViewById(R.id.reqfea);
                viewHolder.textView3 = convertView.findViewById(R.id.reqkey);
                viewHolder.textView4 = convertView.findViewById(R.id.reqnav);
                viewHolder.textView5 = convertView.findViewById(R.id.reqtouch);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ConfigurationInfo configurationInfo = (ConfigurationInfo) mAdapterList.get(index).vanillaItem;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // GLES ver
            viewHolder.textView1.setText(getString(R.string.gles_ver) + ": " + Utils.getOpenGL(configurationInfo.reqGlEsVersion));

            // Flag & others
            viewHolder.textView2.setText(getString(R.string.input_features) + ": " + configurationInfo.reqInputFeatures);

            viewHolder.textView3.setText("KeyboardType" + ": " + configurationInfo.reqKeyboardType);

            viewHolder.textView4.setText("Navigation" + ": " + configurationInfo.reqNavigation);

            viewHolder.textView5.setText("Touchscreen" + ": " + configurationInfo.reqTouchScreen);


            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        @NonNull
        @SuppressLint("SetTextI18n")
        private View getSignatureView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(mActivity);
            }

            TextView textView = (TextView) convertView;
            final Signature signature = (Signature) mAdapterList.get(index).vanillaItem;
            textView.setText(Utils.signCert(signature) + "\n" + signature.toCharsString());
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setTextIsSelectable(true);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, 0, medium_size, small_size);

            return convertView;
        }

        /**
         * Here we check if recycled view match requested type. Tag can be null if recycled view comes from
         * groups that doesn't implement {@link ViewHolder}, such as groups that use only a simple text view.
         */
        private boolean checkIfConvertViewMatch(View convertView, @Property int requestedGroup) {
            return convertView == null || convertView.getTag() == null || ((ViewHolder) convertView.getTag()).currentViewType != requestedGroup;
        }
    }

    @NonNull
    private String permAppOp(String s) {
        if (Build.VERSION.SDK_INT >= 23 && AppOpsManager.permissionToOp(s) != null) {
            return "\nAppOP> " + AppOpsManager.permissionToOp(s);
        } else {
            return "";
        }
    }
}