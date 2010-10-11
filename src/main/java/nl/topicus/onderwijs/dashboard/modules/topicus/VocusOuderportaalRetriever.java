package nl.topicus.onderwijs.dashboard.modules.topicus;

import static nl.topicus.onderwijs.dashboard.modules.topicus.RetrieverUtils.getStatuspage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;
import nl.topicus.onderwijs.dashboard.modules.Project;
import nl.topicus.onderwijs.dashboard.modules.Repository;

import org.apache.wicket.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VocusOuderportaalRetriever implements
		Repository<TopicusApplicationStatus> {
	private static final Logger log = LoggerFactory
			.getLogger(VocusOuderportaalRetriever.class);
	private Map<Project, List<String>> statusUrls = new HashMap<Project, List<String>>();

	public VocusOuderportaalRetriever() {
		statusUrls.put(new Project("atvo_ouders", "@VO Ouderportaal"), Arrays
				.asList("https://start.vocuslis.nl/ouders/status",
						"https://start2.vocuslis.nl/ouders/status"));

	}

	public static void main(String[] args) {
		VocusOuderportaalRetriever retriever = new VocusOuderportaalRetriever();
		retriever
				.getProjectData(new Project("atvo_ouders", "@VO Ouderportaal"));
	}

	@Override
	public TopicusApplicationStatus getProjectData(Project project) {
		List<String> urls = statusUrls.get(project);
		if (urls == null || urls.isEmpty()) {
			TopicusApplicationStatus status = new TopicusApplicationStatus();
			status.setVersion("n/a");
			return status;
		}
		int numberOfOfflineServers = 0;
		TopicusApplicationStatus status = new TopicusApplicationStatus();
		status.setNumberOfServers(urls.size());
		for (String statusUrl : urls) {
			try {
				StatusPageResponse statuspage = getStatuspage(statusUrl);
				if (statuspage.isOffline()) {
					numberOfOfflineServers++;
					continue;
				}
				String page = statuspage.getPageContent();

				Source source = new Source(page);

				source.fullSequentialParse();

				List<Element> tableHeaders = source
						.getAllElements(HTMLElementName.TH);
				for (Element tableHeader : tableHeaders) {
					String contents = tableHeader.getContent().toString();
					if ("Applicatie".equals(contents)) {
						// getApplicationVersion(status, tableHeader);
					} else if ("Actieve sessies".equals(contents)) {
						getNumberOfUsers(status, tableHeader);
					} else if ("Start tijd".equals(contents)) {
						getStartTijd(status, tableHeader);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		status.setNumberOfServersOnline(status.getNumberOfServers()
				- numberOfOfflineServers);
		log.info("Application status: {}->{}", project, status);
		return status;
	}

	/*
	 * <div class="yui-u first"> <h2><span>Sessies/Requests</span></h2> <table
	 * style="width:100%;margin-left:20px;"> <colgroup><col style="width:50%"
	 * /><col style="width:50%" /></colgroup> <tr><th>Actieve
	 * sessies</th><td>422</td></tr> <tr><th>Gecreëerde
	 * sessies</th><td>90505</td></tr> <tr><th>Piek
	 * sessies</th><td>706</td></tr> <tr><th>Actieve
	 * requests</th><td>3</td></tr> </table> </div>
	 */
	private Integer getNumberOfUsers(TopicusApplicationStatus status,
			Element tableHeader) {
		Element sessiesCell = tableHeader.getParentElement().getContent()
				.getFirstElement("td");

		int currentNumberOfUsers = status.getNumberOfUsers() == null ? 0
				: status.getNumberOfUsers();

		String tdContents = sessiesCell.getContent().getTextExtractor()
				.toString();
		Integer numberOfUsersOnServer = Integer.valueOf(tdContents);
		status.setNumberOfUsers(currentNumberOfUsers + numberOfUsersOnServer);
		return numberOfUsersOnServer;
	}

	/*
	 * <div class="yui-u"> <h2><span>Applicatie status</span></h2> <table
	 * style="width:100%;margin-left:20px;"> <colgroup><col style="width:50%"
	 * /><col style="width:50%" /></colgroup> <tr><th>Start tijd</th><td>4
	 * oktober 2010, 17:45</td></tr> <tr><th>Beschikbaarheid</th><td>6.1
	 * days</td></tr> <tr><th>Volgende update instellingen</th><td>N/A</td></tr>
	 * <tr><th>Status</th><td>OK</td></tr> </table> </div>
	 */

	private Date getStartTijd(TopicusApplicationStatus status,
			Element tableHeader) {
		Element starttijdCell = tableHeader.getParentElement().getContent()
				.getFirstElement("td");
		String starttijdText = starttijdCell.getContent().getTextExtractor()
				.toString();
		SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, hh:mm",
				new Locale("NL"));
		try {
			Date starttime = sdf.parse(starttijdText);
			Date now = new Date();
			status.setUptime(Duration.milliseconds(
					now.getTime() - starttime.getTime()).getMilliseconds());
			return starttime;
		} catch (ParseException e) {
			log.error("Unable to parse starttime " + starttijdText
					+ " according to format dd MMMM yyyy, hh:mm", e);
			return null;
		}
	}
}