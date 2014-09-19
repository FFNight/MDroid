package in.co.praveenkumar.mdroid.fragment;

import in.co.praveenkumar.mdroid.apis.R;
import in.co.praveenkumar.mdroid.helper.ModuleIcon;
import in.co.praveenkumar.mdroid.helper.SessionSetting;
import in.co.praveenkumar.mdroid.moodlemodel.MoodleModule;
import in.co.praveenkumar.mdroid.moodlemodel.MoodleModuleContent;
import in.co.praveenkumar.mdroid.moodlemodel.MoodleSection;
import in.co.praveenkumar.mdroid.task.CourseContentSyncTask;
import in.co.praveenkumar.mdroid.task.DownloadTask;
import in.co.praveenkumar.mdroid.view.StickyListView;
import in.co.praveenkumar.mdroid.view.StickyListView.PinnedSectionListAdapter;

import java.util.ArrayList;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ContentFragment extends Fragment {
	Context context;
	long coursedbid;
	int courseid;
	CourseListAdapter courseContentListAdapter;
	SessionSetting session;
	ArrayList<CourseContentObject> listObjects = new ArrayList<CourseContentObject>();

	/**
	 * This constructor is required to prevent exceptions on app usage. Don't
	 * use this constructor.
	 */
	public ContentFragment() {
	}

	/**
	 * 
	 * @param courseid
	 *            Choose which course contents to be listed.
	 * @param coursedbid
	 *            Database id of the course. This is required for section
	 *            details sync
	 */
	public ContentFragment(int courseid, long coursedbid) {
		this.courseid = courseid;
		this.coursedbid = coursedbid;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View rootView = inflater.inflate(R.layout.frag_content, container,
				false);
		this.context = getActivity();
		session = new SessionSetting(context);

		CourseContentSyncTask ccs = new CourseContentSyncTask(
				session.getmUrl(), session.getToken(),
				session.getCurrentSiteId());
		ArrayList<MoodleSection> sections = ccs.getCourseContents(courseid);
		mapSectionsToListObjects(sections);

		ListView courseList = (ListView) rootView
				.findViewById(R.id.list_course_content);
		courseContentListAdapter = new CourseListAdapter(context);
		((StickyListView) courseList).setShadowVisible(false);
		courseList.setAdapter(courseContentListAdapter);

		new listCoursesThread(session.getmUrl(), session.getToken(), courseid,
				coursedbid, session.getCurrentSiteId()).execute("");

		return rootView;
	}

	private class listCoursesThread extends AsyncTask<String, Integer, Boolean> {
		CourseContentSyncTask ccs;
		int courseid;
		Long coursedbid;
		Boolean syncStatus;

		public listCoursesThread(String mUrl, String token, int courseid,
				Long coursedbid, Long siteid) {
			ccs = new CourseContentSyncTask(mUrl, token, siteid);
			this.courseid = courseid;
			this.coursedbid = coursedbid;
		}

		@Override
		protected void onPreExecute() {
			System.out.println("Pre execute");
			// mSections = ccs.getCourseContents(courseid);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			System.out.println("Background execute");
			syncStatus = ccs.syncCourseContents(courseid, coursedbid);
			ArrayList<MoodleSection> sections = ccs.getCourseContents(courseid);

			// Save all sections into a listObject array for easy access inside
			mapSectionsToListObjects(sections);

			if (syncStatus)
				return true;
			else
				return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			courseContentListAdapter.notifyDataSetChanged();
		}

	}

	public class CourseListAdapter extends ArrayAdapter<String> implements
			PinnedSectionListAdapter {
		static final int TYPE_MODULE = 0;
		static final int TYPE_HEADER = 1;
		static final int TYPE_COUNT = 2;
		final Context context;

		public CourseListAdapter(Context context) {
			super(context, R.layout.list_item_account, new String[listObjects
					.size()]);
			this.context = context;
		}

		@Override
		public int getViewTypeCount() {
			return TYPE_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			return listObjects.get(position).viewType;
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			ViewHolder viewHolder;
			int type = getItemViewType(position);

			if (convertView == null) {
				viewHolder = new ViewHolder();
				LayoutInflater inflater = (LayoutInflater) context
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

				// Choose layout
				switch (type) {
				case TYPE_HEADER:
					convertView = inflater.inflate(R.layout.list_item_section,
							parent, false);

					viewHolder.sectionname = (TextView) convertView
							.findViewById(R.id.list_sectionname);
					break;

				case TYPE_MODULE:
					convertView = inflater.inflate(R.layout.list_item_module,
							parent, false);

					viewHolder.modulename = (TextView) convertView
							.findViewById(R.id.list_modulename);
					viewHolder.moduledesc = (TextView) convertView
							.findViewById(R.id.list_moduledescription);
					viewHolder.moduleicon = (ImageView) convertView
							.findViewById(R.id.list_moduleicon);
					break;
				}

				// Save the holder with the view
				convertView.setTag(viewHolder);
			} else {
				// Just use the viewHolder and avoid findviewbyid()
				viewHolder = (ViewHolder) convertView.getTag();
			}

			// Assign values
			switch (type) {
			case TYPE_HEADER:
				viewHolder.sectionname
						.setText(listObjects.get(position).sectionname);
				break;

			case TYPE_MODULE:
				MoodleModule module = listObjects.get(position).module;
				viewHolder.modulename.setText(module.getName());
				viewHolder.moduleicon.setImageBitmap(ModuleIcon.of(module,
						context));
				String description = module.getDescription();
				if (description == null)
					description = "";
				else
					description = Html.fromHtml(description).toString().trim();
				viewHolder.moduledesc.setText(description);
				break;
			}
			convertView.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					if (listObjects.get(position).module.getContents() != null) {
						MoodleModuleContent content = listObjects.get(position).module
								.getContents().get(0);
						String url = content.getFileurl();
						url += "&token=" + session.getToken();
						DownloadTask dt = new DownloadTask(context);
						dt.download(url, content.getFilename(), true,
								DownloadTask.SYSTEM_DOWNLOADER);
					}
				}
			});

			return convertView;
		}

		@Override
		public boolean isItemViewTypePinned(int viewType) {
			if (viewType == TYPE_HEADER)
				return true;
			else
				return false;
		}
	}

	static class ViewHolder {
		TextView sectionname;
		TextView modulename;
		ImageView moduleicon;
		TextView moduledesc;
	}

	/**
	 * This is used to hold all data of any list item in the coursecontent
	 * listview. A list can be a Module or a section header. This view will be
	 * to get appropriate view from position for our stickylistview which has
	 * multiple view types
	 * 
	 * @author praveen
	 * 
	 */
	class CourseContentObject {
		/**
		 * Follows type values as defined in CourseListAdapter. Check
		 * CourseListAdapter.TYPE_ for available values
		 */
		int viewType;
		/**
		 * Name of the section to which this object belongs
		 */
		String sectionname;
		/**
		 * Id of the section to which this object belongs
		 */
		int sectionid;
		/**
		 * Module object. (Only for viewType = TYPE_MODULE)
		 */
		MoodleModule module;

	}

	private void mapSectionsToListObjects(ArrayList<MoodleSection> sections) {
		if (sections == null)
			return;

		MoodleSection section;
		ArrayList<MoodleModule> modules;
		for (int i = 0; i < sections.size(); i++) {
			section = sections.get(i);
			modules = section.getModules();
			if (modules.size() > 0) {
				CourseContentObject object = new CourseContentObject();
				object.viewType = CourseListAdapter.TYPE_HEADER;
				object.sectionid = section.getSectionid();
				object.sectionname = section.getName();
				listObjects.add(object);

				// Add modules
				for (int j = 0; j < modules.size(); j++) {
					CourseContentObject mObject = new CourseContentObject();
					mObject.viewType = CourseListAdapter.TYPE_MODULE;
					mObject.sectionid = section.getSectionid();
					mObject.sectionname = section.getName();
					mObject.module = modules.get(j);
					listObjects.add(mObject);
				}
			}
		}
	}

}