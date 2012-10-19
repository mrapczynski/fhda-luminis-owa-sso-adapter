<%@ Page Language="C#" AutoEventWireup="true" %>

<%--

MOWA 2010 for GCF Setup File
Author: Matt Rapczynski, Foothill-De Anza Community College District
Date: June 18, 2012

This is a shim to fit between the Luminis portal, and the initial call
to the GCF connector to begin a single sign-on transaction. The goal of this
file to dump the cookies from the MOWA domain, and then begin
single sign-on process for the user.

--%>

<%--

INSTRUCTIONS:

1. Update exchange.yourschool.edu to match the domain name for your Exchange OWA installation on line 42. Be careful
not to damage of any of the HTML encoded characters.

--%>

<%
	// Get all ASP.NET session cookies for OWA
	String[] clientCookieKeys = Request.Cookies.AllKeys;

	// Loop through cookies individually
	foreach(String cookieKey in clientCookieKeys) {
		// Set the cookie as expired so we can dump it
		Response.Cookies[cookieKey].Expires = DateTime.Now.AddDays(-1D);
	}
%>

<!DOCTYPE html>

<html>
	<head runat="server">
		<title>Setting up Microsoft Outlook</title>

		<script type="text/javascript">
			// Redirect to the Luminis GCF connector for Exchange/MOWA
			window.location = "https://<%= Request["host"] %>/cp/ip/login?sys=exchange&url=https%3A%2F%2Fexchange.yourschool.edu%2Fowa%2F";
		</script>

		<style type="text/css">
			html {
				margin: 0px;
				padding: 0px;
			}

			body {
				margin: 0px;
				padding: 15px;
				font-family: Helvetica, Arial, Verdana, sans-serif;
				font-size: 12px;
			}

			td {
				vertical-align: middle;
				text-align: left;
			}
		</style>
	</head>

	<body>
		<table>
			<tbody>
				<tr>
					<td style="padding-right: 10px;"><img src="./gcf_working.png" /></td>
					<td style="color: #888;">Setting up Microsoft Outlook Web Application...</td>
				</tr>
			</tbody>
		</table>
	</body>
</html>
