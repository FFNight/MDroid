package in.co.praveenkumar.mdroid.task;

import in.co.praveenkumar.mdroid.moodlemodel.MoodleModule;
import in.co.praveenkumar.mdroid.moodlemodel.MoodleModuleContent;
import in.co.praveenkumar.mdroid.moodlemodel.MoodleSection;
import in.co.praveenkumar.mdroid.moodlerest.MoodleRestCourseContent;

import java.util.ArrayList;
import java.util.List;

public class CourseContentSyncTask {
	String mUrl;
	String token;
	long siteid;
	int courseid;
	String error;

	public CourseContentSyncTask(String mUrl, String token, long siteid) {
		this.mUrl = mUrl;
		this.token = token;
		this.siteid = siteid;
	}

	/**
	 * Sync all sections in a course in the current site. <br/>
	 * Note: This call has network activity and so, any calls made must be from
	 * a background thread
	 * 
	 * @param courseid
	 *            Moodle courseid of the course
	 * @param coursedbid
	 *            Id of the course in the database. When a course is saved into
	 *            the sugar db, a unique id is assigned to it. This is required
	 *            to link the contents with this course for offline use. This id
	 *            can be obtained by calling getId() on your MoodleCourse object
	 * @return syncStatus
	 */
	public Boolean syncCourseContents(int courseid, Long coursedbid) {
		this.courseid = courseid;

		MoodleRestCourseContent mrcc = new MoodleRestCourseContent(mUrl, token);
		ArrayList<MoodleSection> mSections = mrcc.getCourseContent(courseid
				+ "");

		/** Error checking **/
		if (mSections == null) {
			error = "Network issue!";
			return false;
		}

		// Some network or encoding issue.
		if (mSections.size() == 0) {
			error = "No data received! Permissions issue?";
			return false;
		}

		// Moodle exception
		if (mSections.size() == 1 && mSections.get(0).getCourseid() == 0) {
			error = "Moodle Exception: User don't have permissions!";
			return false;
		}

		// Add relational fields to all sections and update
		MoodleSection section = new MoodleSection();
		List<MoodleSection> dbSections;
		for (int i = 0; i < mSections.size(); i++) {
			section = mSections.get(i);
			section.setSiteid(siteid);
			section.setCourseid(courseid);
			section.setParentid(coursedbid);

			// Update or save in database
			/*
			 * -TODO- Should more conditions be added?
			 */
			dbSections = MoodleSection.find(MoodleSection.class,
					"sectionid = ? and siteid = ?",
					section.getSectionid() + "", section.getSiteid() + "");
			if (dbSections.size() > 0)
				section.setId(dbSections.get(0).getId()); // updates on save()
			section.save();

			// Now loop all modules in this section
			syncModules(section.getModules(), section.getId(),
					section.getSectionid());
		}

		return true;
	}

	/**
	 * 
	 * @param modules
	 *            ArrayList of modules to be synced
	 * @param sectiondbid
	 *            Database id of the section to which these modules belong
	 * @param sectionid
	 *            Moodle sectionid of the section to which these modules belong
	 */
	private void syncModules(ArrayList<MoodleModule> modules, Long sectiondbid,
			int sectionid) {

		// No modules in section
		if (modules == null)
			return;

		// Add relational fields to all modules and update
		MoodleModule module = new MoodleModule();
		List<MoodleModule> dbModules;
		for (int i = 0; i < modules.size(); i++) {
			module = modules.get(i);
			module.setSiteid(siteid);
			module.setCourseid(courseid);
			module.setParentid(sectiondbid);
			module.setSectionid(sectionid);

			// Update or save in database
			/*
			 * -TODO- Should more conditions be added?
			 */
			dbModules = MoodleModule.find(MoodleModule.class,
					"moduleid = ? and siteid = ?", module.getModuleid() + "",
					module.getSiteid() + "");
			if (dbModules.size() > 0)
				module.setId(dbModules.get(0).getId()); // updates on save()
			module.save();

			// Now loop all Module contents in this module
			syncModuleContents(module.getContents(), module.getId(),
					module.getModuleid(), sectionid);
		}

	}

	/**
	 * 
	 * @param modulecontents
	 *            ArrayList of modulecontent to be synced
	 * @param moduledbid
	 *            Database id of the module to which these contents belong
	 * @param moduleidid
	 *            Moodle moduleid of the module to which these contents belong
	 * @param sectionid
	 *            Moodle sectionid of the section to which these modules belong
	 */
	private void syncModuleContents(ArrayList<MoodleModuleContent> contents,
			Long moduledbid, int moduleid, int sectionid) {

		// No modules in section
		if (contents == null)
			return;

		// Add relational fields to all modules and update
		MoodleModuleContent content = new MoodleModuleContent();
		List<MoodleModuleContent> dbContents;
		for (int i = 0; i < contents.size(); i++) {
			content = contents.get(i);
			content.setSiteid(siteid);
			content.setCourseid(courseid);
			content.setParentid(moduledbid);
			content.setSectionid(sectionid);
			content.setModuleid(moduleid);

			// Update or save in database
			dbContents = MoodleModuleContent.find(MoodleModuleContent.class,
					"parentid = ? and siteid = ?", content.getParentid() + "",
					content.getSiteid() + "");
			if (dbContents.size() > 0)
				content.setId(dbContents.get(0).getId()); // updates on save()
			content.save();
		}

	}

	/**
	 * Get a list of all sections is a Course. <br/>
	 * Note: Depending on the contents of a course, this could take some time as
	 * it runs many sql queries. It is recommended that this method is called
	 * from a background thread
	 * 
	 * @param courseid
	 *            Moodle courseid of the course
	 * @return List of sections
	 */
	public ArrayList<MoodleSection> getCourseContents(int courseid) {
		List<MoodleSection> sections = MoodleSection.find(MoodleSection.class,
				"courseid = ? and siteid = ?", courseid + "", siteid + "");

		// Add modules to sections
		List<MoodleModule> dbModules;
		List<MoodleModuleContent> dbContents;
		for (int i = 0; i < sections.size(); i++) {
			dbModules = MoodleModule.find(MoodleModule.class, "parentid = ?",
					sections.get(i).getId() + "");

			// Set module contents to modules
			for (int j = 0; j < dbModules.size(); j++) {
				dbContents = MoodleModuleContent.find(
						MoodleModuleContent.class, "parentid = ?", dbModules
								.get(j).getId() + "");
				dbModules.get(j).setContents(dbContents);
			}

			sections.get(i).setModules(dbModules);
		}

		return new ArrayList<MoodleSection>(sections);
	}
}