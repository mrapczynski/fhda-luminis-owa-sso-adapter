## Introduction

The Luminis OWA (Outlook Web Application) SSO adapter is a shim packaged as a deployable Java web application that permits the Luminis GCF framework to do single sign-on into Microsoft Exchange OWA while retaining the full *premium mode* user experience.

Luminis Platform 4.x comes pre-packaged with a GCF connector compatible with OWA 2007/2010. The big downside is that these connectors can only provide single sign-on for the light or vision assistance mode of OWA.

## How Does It Work?

When using forms based authentication, OWA uses multiple strategies to detect and enable the premium mode from the client side. It appears that user agent detection may be used to provide premium mode only to browsers designated as supported by Microsoft. In addition, multiple cookies are used to communicate the intent to use premium mode during authentication.

It is impossible to fully emulate all of this activity in the XML for a GCF connector, and instead this adapter application can be used to perform the necessary steps in a servlet. The GCF can call the servlet to do the authentication work instead of calling OWA directly. The servlet behaves in such a way that all the essential features of the GCF framework, including client cookie pickup, work without issue.

This adapter was developed and tested on Luminis Platform 4.3, but is likely to be compatible with earlier versions of 4.x without changes. It has been in production for many months, and thus far has proven to be very reliable.

## Requirements

* Luminis Platform 4.x **(tested)** or 5.x **(untested)**
* Microsoft Exchange OWA (tested on 2010, but not 2007)
* Basic knowledge of Maven
* Basic knowledge of configuring and installing a GCF connector
* Linux is assumed, but adapt the directions as needed for Windows deployments

## License and Warranty

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## Installation

### Step 1 - Get the Source

Clone this Git repository to your disk, or download the package ZIP file using the button provided above.

### Step 2 - Configure the Application

The adapter can be configured for your OWA deployment by editing the four context parameters in src/main/java/webapp/WEB-INF/web.xml

| Context Parameter | Value |
| ----------------- | ----- |
| mowa\_base\_url | Use the full URL to your OWA installation for this parameter, and include the trailing "/". **Example:** https://exchange.yourschool.edu/owa/ |
| mowa\_domain | The domain name to your OWA installation. **Example:** exchange.yourschool.edu |
| ssl\_trust\_file | The name of the keytool generated trust file from your SSL certificate. This must be stored in the WEB-INF directory and nowhere else. |
| ssl\_trust\_password | The password to your trust file as specified during the keytool process. |

**Note on SSL Support:** SSL is supported, but you must be able to take the certificate for your Exchange domain (i.e. a .cer file) and pass it through the Java keytool to generate a trust file. Here is an easy example to follow: http://docs.oracle.com/javase/tutorial/security/toolfilex/rstep1.html - Place the generated keystore file in the WEB-INF directory of the adapter application.

### Step 3 - Build

Using Maven on the command line (or in your favorite Java IDE), run the following Maven goals in sequence on the root directory of the project: **clean compile war:war**

In the _target_ directory, a WAR archive will be generated with the name **fhda-owa.war**

### Step 4 - Deploy

This part is tricky because every school has a different Luminis deployment. Some are single-tier boxes, while others are multi-tier parallel deployments.

You will need to make an executive decision about how to deploy this application. Here are some ideas:

1. In a single-tier environment, deploy on products/tomcat/tomcat-cp in the /webapps directory
2. In a parallel deployment environment, deploy in products/tomcat/tomcat-cp **on each portal tier** within the /webapps directory. The GCF connector can reach the adapter through your load balancer.
3. Deploy in a separate standalone Tomcat instance (or other J2EE container of your choice)

Make sure that the GCF framework on your resource tier can reach the adapter application, and that the deployment container of choice can talk to Exchange OWA.

This adapter was developed (and is currently deployed) in a parallel deployment environment with 1 resource tier, and 4 portal/web tiers. Therefore, option 2 was my choice.

### Step 5 - Test the Adapter

Assuming you have no errors from your container where you deployed the WAR file, now we can test it. Use a generic HTTP client to create a GET or POST request to adapter.

* On **Mac OSX**, try using HTTP Client (App Store) or GNU wget through Terminal
* On **Windows**, Fiddler is a great HTTP debugging tool
* On **UNIX**, wget or curl are good debugging tools 

Only two request parameters are expected:

1. mailboxuser - The valid username of an Exchange mailbox
2. mailboxpassword - The password to the specific mailbox

Example URL: https://yourportal.yourschool.edu/fhda-owa/proxy?mailboxuser=jdoe&mailboxpassword=secret

If all is well, no errors will appear in your container log, and you should get back an HTML response that clearly shows messages in your inbox, and all of the features of OWA premium mode. 

An authentication failure will return an HTML result with the OWA login page.

### Step 6 - Configure and Install GCF Connector

I anticipate most Luminis schools already have staff with at least a little GCF experience, so I will not go into detail with a GCF how-to.

A GCF connector XML file, properties, and "configman" file are contained in the luminis/4.x directory within the project source code. Feel free to tweak the adapter for Luminis 5.x - it may not need any changes. These files should be placed in your $CP_HOME/webapps/cpipconnector/WEB-INF/config directory

1. In exchange.properties, you need to set up values for the gcfAdapterServer and gcfAdapterURL properties to point to where you deployed the adapter in step 4.

2. For the pickup.remoteurl property, this should be the full URL to the GCF cookie pickup file that should be installed all of your Exchange CAS servers.

3. Update exchange.configman with the correct URL to your GCF connector of your portal installation. Use the configman -i command to import this file into your Luminis configuration.

Step 6 is an appropriate time to discuss authentication and credentials. I designed this adapter around the premise that Exchange **uses the same exact credentials as the Luminis portal**. How is this possible? I established real-time synchronization from the Luminis LDAP to the Active Directory domain used for Exchange. Thus, the GCF properties I deliver in this source code are biased to sending the pdsLoginId and userPassword attributes of Luminis for authentication.

I expect that many institutions who want single sign-on for Exchange are not doing this, and therefore you need to take care to configure the GCF properties (and the configman settings) to send the right username and password. If you are already using the Ellucian provided GCF connector for OWA, but stuck in light mode, copy those settings into this GCF connector for the same authentication behavior.

### Step 7 - Install GCF supplemental files

This step is critical. I learned the hard way that the manner in which OWA deals with cookies, and what it expects to see on the client side, can make or break the way this whole arrangement works. To aid this process, I developed a few extra supplemental materials.

1. **prepare_luminis.aspx** - This executable ASP.NET file set all of the existing cookies associated with previous sessions on your Exchange OWA domain to be deleted. It is good to do this prior to attempting a new single sign-on session so that cookies do not get mixed up when the GCF pickup file tries to place new cookies on the client. This dynamic web service approach proved *far more reliable* than using JavaScript to do the same thing.

2. **luminis_pickup.html** - Ellucian factory GCF pickup with a little bit of UI. If a user gets caught waiting for a few seconds, the text and image will indicate to them OWA is still loading. Same thing applies to the prepare_luminis.aspx file.

3. **gcf_working.png** - Handy little gear icon for the two files above. Less boring than a blank white page.

Install these files on **each Exchange CAS server** in the C:\Program Files\Microsoft\Exchange Server\V14\ClientAccess\Owa\auth directory. This is the 2010 directory. If you are using Exchange 2007, this directory is likely to be different.

No reboots should be required.

### Step 8 - Reboot Luminis

Hopefully, you are trying this in your test portal first.

Reboot Luminis to get the new GCF connector picked up. Check $CP_HOME/logs/cpipconnector.log for connector errors on start up.

### Step 9 - Putting It All Together

Everything seems to have booted okay with no errors in the logs, the adapter tests out okay, and it looks like we are ready to try it out in the portal. Now what?

When I test GCF connectors for the first time, I create a targeted content channel just for myself, and put a link to access the connector in it. I have that link target a blank window so I can see the result without being disrupted by the Luminis window frame.

For the OWA adapter, the starting point is the prepare_luminis.aspx file that we installed in the /auth directory on the CAS server. The starting link from your portal should look like the following:

https://exchange.yourschool.edu/owa/auth/prepare_luminis.aspx?host=yourportal.yourschool.edu

Replace the host names in the URL as appropriate. Once the cookies are cleared out at the prepare_luminis.aspx step, the client will be redirected to call the /cp/ip/login service on the Luminis portal. From there on, the GCF will talk to our adapter, authenticate to OWA using the appropriate username and password, and return a set of useful session cookies to the client for opening an OWA session. 

## Making Improvements

Is this complicated? Yes. Can it be made better? Probably. That is why this source code is here on GitHub. Either you can benefit from it, or fork it and make it better.

Offering premium mode with single sign-on in our Luminis portal has been a big hit with our entire user community. Our migration to Exchange partially rode on the back of the idea that the web experience would be a significant improvement over our legacy system. To lose much of that greater experience just to do single sign-on was unacceptable, and therefore the research to make this work proved to be worth it.







