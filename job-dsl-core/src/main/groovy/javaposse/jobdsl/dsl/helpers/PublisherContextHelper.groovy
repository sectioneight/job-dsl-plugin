package javaposse.jobdsl.dsl.helpers

import com.google.common.base.Preconditions
import javaposse.jobdsl.dsl.WithXmlAction

class PublisherContextHelper extends AbstractContextHelper<PublisherContext> {

    PublisherContextHelper(List<WithXmlAction> withXmlActions) {
        super(withXmlActions)
    }

    def publishers(Closure closure) {
        execute(closure, new PublisherContext())
    }

    Closure generateWithXmlClosure(PublisherContext context) {
        return { Node project ->
            def publishersNode
            if (project.publishers.isEmpty()) {
                publishersNode = project.appendNode('publishers')
            } else {
                publishersNode = project.publishers[0]
            }
            context.publisherNodes.each {
                publishersNode << it
            }
        }
    }

    static class PublisherContext implements Context {
        List<Node> publisherNodes = []

        PublisherContext() {
        }

        PublisherContext(List<Node> publisherNodes) {
            this.publisherNodes = publisherNodes
        }

        /**
         <hudson.plugins.emailext.ExtendedEmailPublisher>
             <recipientList>billing@company.com</recipientList>
             <configuredTriggers>
                 <hudson.plugins.emailext.plugins.trigger.FailureTrigger>
                     <email>
                         <recipientList/>
                         <subject>$PROJECT_DEFAULT_SUBJECT</subject>
                         <body>$PROJECT_DEFAULT_CONTENT</body>
                         <sendToDevelopers>false</sendToDevelopers>
                         <sendToRequester>false</sendToRequester>
                         <includeCulprits>false</includeCulprits>
                         <sendToRecipientList>true</sendToRecipientList>
                     </email>
                 </hudson.plugins.emailext.plugins.trigger.FailureTrigger>
                 <hudson.plugins.emailext.plugins.trigger.SuccessTrigger>
                     <email>
                         <recipientList/>
                         <subject>$PROJECT_DEFAULT_SUBJECT</subject>
                         <body>$PROJECT_DEFAULT_CONTENT</body>
                         <sendToDevelopers>false</sendToDevelopers>
                         <sendToRequester>false</sendToRequester>
                         <includeCulprits>false</includeCulprits>
                         <sendToRecipientList>true</sendToRecipientList>
                     </email>
                 </hudson.plugins.emailext.plugins.trigger.SuccessTrigger>
             </configuredTriggers>
             <contentType>default</contentType>
             <defaultSubject>$DEFAULT_SUBJECT</defaultSubject>
             <defaultContent>$DEFAULT_CONTENT</defaultContent>
             <attachmentsPattern/>
         </hudson.plugins.emailext.ExtendedEmailPublisher>
         * @return
         * TODO Support list for recipients
         * TODO Escape XML for all subject and content fields
         */
        def extendedEmail(String recipients = null, String subjectTemplate = null, String contentTemplate = null, Closure emailClosure = null) {
            EmailContext emailContext = new EmailContext()
            AbstractContextHelper.executeInContext(emailClosure, emailContext)

            // Validate that we have the typical triggers, if nothing is provided
            if (emailContext.emailTriggers.isEmpty()) {
                emailContext.emailTriggers << new EmailTrigger('Failure')
                emailContext.emailTriggers << new EmailTrigger('Success')
            }

            recipients = recipients?:'$DEFAULT_RECIPIENTS'
            subjectTemplate = subjectTemplate?:'$DEFAULT_SUBJECT'
            contentTemplate = contentTemplate?:'$DEFAULT_CONTENT'

            // Now that the context has what we need
            def nodeBuilder = NodeBuilder.newInstance()
            def emailNode = nodeBuilder.'hudson.plugins.emailext.ExtendedEmailPublisher' {
                recipientList recipients
                contentType 'default'
                defaultSubject subjectTemplate
                defaultContent contentTemplate
                attachmentsPattern ''

                configuredTriggers {
                    emailContext.emailTriggers.each { EmailTrigger trigger ->
                        "hudson.plugins.emailext.plugins.trigger.${trigger.triggerShortName}Trigger" {
                            email {
                                recipientList trigger.recipientList
                                subject trigger.subject
                                body trigger.body
                                sendToDevelopers trigger.sendToDevelopers
                                sendToRequester trigger.sendToRequester
                                includeCulprits trigger.includeCulprits
                                sendToRecipientList trigger.sendToRecipientList
                            }
                        }
                    }
                }
            }

            // Apply their overrides
            if (emailContext.configureClosure) {
                emailContext.configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
                WithXmlAction action = new WithXmlAction(emailContext.configureClosure)
                action.execute(emailNode)
            }

            publisherNodes << emailNode
        }

        /**
         <hudson.tasks.ArtifactArchiver>
           <artifacts>build/libs/*</artifacts>
           <latestOnly>false</latestOnly>
         </hudson.tasks.ArtifactArchiver>
         * @param glob
         * @param excludeGlob
         * @param latestOnly
         */
        def archiveArtifacts(String glob, String excludeGlob = null, Boolean latestOnlyBoolean = false) {
            def nodeBuilder = new NodeBuilder()

            Node archiverNode = nodeBuilder.'hudson.tasks.ArtifactArchiver' {
                artifacts glob
                if(excludeGlob) {
                    excludes excludeGlob
                }
                latestOnly latestOnlyBoolean?'true':'false'
            }

            publisherNodes << archiverNode
        }

        /**
         * Everything checked:
         <hudson.tasks.junit.JUnitResultArchiver>
             <testResults>build/test/*.xml</testResults> // Can be empty
             <keepLongStdio>true</keepLongStdio>
             <testDataPublishers> // Empty if no extra publishers
                 <hudson.plugins.claim.ClaimTestDataPublisher/> // Allow claiming of failed tests
                 <hudson.plugins.junitattachments.AttachmentPublisher/> // Publish test attachments
             </testDataPublishers>
         </hudson.tasks.junit.JUnitResultArchiver>
         */
        def archiveJunit(String glob, boolean retainLongStdout = false, boolean allowClaimingOfFailedTests = false, boolean publishTestAttachments = false) {
            def nodeBuilder = new NodeBuilder()

            Node archiverNode = nodeBuilder.'hudson.tasks.junit.JUnitResultArchiver' {
                testResults glob
                keepLongStdio retainLongStdout?'true':'false'
                testDataPublishers {
                    if (allowClaimingOfFailedTests) {
                        'hudson.plugins.claim.ClaimTestDataPublisher' ''
                    }
                    if (publishTestAttachments) {
                        'hudson.plugins.junitattachments.AttachmentPublisher' ''
                    }
                }
            }

            publisherNodes << archiverNode

        }

        /**
        <htmlpublisher.HtmlPublisher>
          <reportTargets>
            <htmlpublisher.HtmlPublisherTarget>
              <reportName>Gradle Tests</reportName>
              <reportDir>build/reports/tests/</reportDir>
              <reportFiles>index.html</reportFiles>
              <keepAll>false</keepAll>
              <wrapperName>htmlpublisher-wrapper.html</wrapperName>
            </htmlpublisher.HtmlPublisherTarget>
          </reportTargets>
        </htmlpublisher.HtmlPublisher>
        */
        def publishHtml(Closure htmlReportContext) {
            HtmlReportContext reportContext = new HtmlReportContext()
            AbstractContextHelper.executeInContext(htmlReportContext, reportContext)

            // Now that the context has what we need
            def nodeBuilder = NodeBuilder.newInstance()
            def htmlPublisherNode = nodeBuilder.'htmlpublisher.HtmlPublisher' {
                reportTargets {
                    reportContext.targets.each { HtmlPublisherTarget target ->
                        'htmlpublisher.HtmlPublisherTarget' {
                            // All fields can have a blank, odd.
                            reportName target.reportName
                            reportDir target.reportDir
                            reportFiles target.reportFiles
                            keepAll target.keepAll
                            wrapperName target.wrapperName
                        }
                    }
                }
            }
            publisherNodes << htmlPublisherNode
        }
        /**
         * With only the target specified:
         <hudson.plugins.jabber.im.transport.JabberPublisher>
             <targets>
                 <hudson.plugins.im.GroupChatIMMessageTarget>
                     <name>api@conference.jabber.netflix.com</name>
                     <notificationOnly>false</notificationOnly>
                 </hudson.plugins.im.GroupChatIMMessageTarget>
             </targets>
             <strategy>ALL</strategy> // all
             or <strategy>FAILURE_AND_FIXED</strategy> // failure and fixed
             or <strategy>ANY_FAILURE</strategy> // failure
             or <strategy>STATECHANGE_ONLY</strategy> // change
             <notifyOnBuildStart>false</notifyOnBuildStart> // Notify on build starts
             <notifySuspects>false</notifySuspects> // Notify SCM committers
             <notifyCulprits>false</notifyCulprits> // Notify SCM culprits
             <notifyFixers>false</notifyFixers> // Notify upstream committers
             <notifyUpstreamCommitters>false</notifyUpstreamCommitters> // Notify SCM fixers

             // Channel Notification Message
             <buildToChatNotifier class="hudson.plugins.im.build_notify.DefaultBuildToChatNotifier"/> // Summary + SCM change
             or <buildToChatNotifier class="hudson.plugins.im.build_notify.SummaryOnlyBuildToChatNotifier"/> // Just Summary
             or <buildToChatNotifier class="hudson.plugins.im.build_notify.BuildParametersBuildToChatNotifier"/> // Summary and build parameters
             or <buildToChatNotifier class="hudson.plugins.im.build_notify.PrintFailingTestsBuildToChatNotifier"/> // Summary, SCM changes and failed tests
             <matrixMultiplier>ONLY_CONFIGURATIONS</matrixMultiplier>
         </hudson.plugins.jabber.im.transport.JabberPublisher>
         */
        def publishJabber(String target, Closure jabberClosure = null) {
            publishJabber(target, null, null, jabberClosure)
        }

        def publishJabber(String target, String strategyName, Closure jabberClosure = null) {
            publishJabber(target, strategyName, null, jabberClosure)
        }

        def publishJabber(String target, String strategyName, String channelNotificationName, Closure jabberClosure = null) {
            JabberContext jabberContext = new JabberContext()
            jabberContext.strategyName = strategyName?:'ALL'
            jabberContext.channelNotificationName = channelNotificationName?:'Default'
            AbstractContextHelper.executeInContext(jabberClosure, jabberContext)

            // Validate values
            assert validJabberStrategyNames.contains(jabberContext.strategyName), "Jabber Strategy needs to be one of these values: ${validJabberStrategyNames.join(',')}"
            assert validJabberChannelNotificationNames.contains(jabberContext.channelNotificationName), "Jabber Channel Notification name needs to be one of these values: ${validJabberChannelNotificationNames.join(',')}"

            def nodeBuilder = NodeBuilder.newInstance()
            def publishNode = nodeBuilder.'hudson.plugins.jabber.im.transport.JabberPublisher' {
                targets {
                    'hudson.plugins.im.GroupChatIMMessageTarget' {
                        println "Delegate: ${delegate.class}"
                        delegate.createNode('name', target)
                        notificationOnly 'false'
                    }
                }
                strategy jabberContext.strategyName
                notifyOnBuildStart jabberContext.notifyOnBuildStart?'true':'false'
                notifySuspects jabberContext.notifyOnBuildStart?'true':'false'
                notifyCulprits jabberContext.notifyCulprits?'true':'false'
                notifyFixers jabberContext.notifyFixers?'true':'false'
                notifyUpstreamCommitters jabberContext.notifyUpstreamCommitters?'true':'false'
                buildToChatNotifier('class': "hudson.plugins.im.build_notify.${jabberContext.channelNotificationName}BuildToChatNotifier")
                matrixMultiplier 'ONLY_CONFIGURATIONS'
            }
            publisherNodes << publishNode
        }
        def validJabberStrategyNames = ['ALL', 'FAILURE_AND_FIXED', 'ANY_FAILURE', 'STATECHANGE_ONLY']
        def validJabberChannelNotificationNames = ['Default', 'SummaryOnly', 'BuildParameters', 'PrintFailingTests']
    }

    static class JabberContext implements Context {
        String strategyName = 'ALL' // ALL,  FAILURE_AND_FIXED, ANY_FAILURE, STATECHANGE_ONLY
        boolean notifyOnBuildStart = false
        boolean notifySuspects = false
        boolean notifyCulprits = false
        boolean notifyFixers = false
        boolean notifyUpstreamCommitters = false
        String channelNotificationName = 'Default' // Default, SummaryOnly, BuildParameters, PrintFailingTests

        void strategyName(String strategyName) {
            this.strategyName = strategyName
        }

        void notifyOnBuildStart(boolean notifyOnBuildStart) {
            this.notifyOnBuildStart = notifyOnBuildStart
        }

        void notifySuspects(boolean notifySuspects) {
            this.notifySuspects = notifySuspects
        }

        void notifyCulprits(boolean notifyCulprits) {
            this.notifyCulprits = notifyCulprits
        }

        void notifyFixers(boolean notifyFixers) {
            this.notifyFixers = notifyFixers
        }

        void notifyUpstreamCommitters(boolean notifyUpstreamCommitters) {
            this.notifyUpstreamCommitters = notifyUpstreamCommitters
        }

        void channelNotificationName(String channelNotificationName) {
            this.channelNotificationName = channelNotificationName
        }
// TODO Create Enum for channelNotificationMessage and strategy
    }

    static class EmailTrigger {
        EmailTrigger(triggerShortName, recipientList = null, subject = null, body = null, sendToDevelopers = null, sendToRequester = null, includeCulprits = null, sendToRecipientList = null) {
            // Use elvis operator to assign default values if needed
            this.triggerShortName = triggerShortName
            this.recipientList = recipientList?:''
            this.subject = subject?:'$PROJECT_DEFAULT_SUBJECT'
            this.body = body ?:'$PROJECT_DEFAULT_CONTENT'
            this.sendToDevelopers = sendToDevelopers==null?false:sendToDevelopers
            this.sendToRequester = sendToRequester==null?false:sendToDevelopers
            this.includeCulprits = includeCulprits==null?false:includeCulprits
            this.sendToRecipientList = sendToRecipientList==null?true:sendToRecipientList
        }

        def triggerShortName, recipientList, subject, body
        def sendToDevelopers, sendToRequester, includeCulprits, sendToRecipientList
    }

    static class EmailContext implements Context {
        def emailTriggerNames = ['PreBuild', 'StillUnstable', 'Fixed', 'Success', 'StillFailing', 'Improvement',
                'Failure', 'Regression', 'Aborted', 'NotBuilt', 'FirstFailure', 'Unstable']
        def emailTriggers = []

        // Not sure why a map syntax wouldn't call method below, so creating this one
        def trigger(Map args) {
            trigger(args.triggerName, args.subject, args.body, args.recipientList, args.sendToDevelopers, args.sendToRequester, args.includeCulprits, args.sendToRecipientList)
        }

        def trigger(String triggerName, String subject = null, String body = null, String recipientList = null,
                    Boolean sendToDevelopers = null, Boolean sendToRequester = null, includeCulprits = null, Boolean sendToRecipientList = null) {
            Preconditions.checkArgument(emailTriggerNames.contains(triggerName), "Possible values: ${emailTriggerNames.join(',')}")

            emailTriggers << new EmailTrigger(triggerName, recipientList, subject, body, sendToDevelopers, sendToRequester, includeCulprits, sendToRecipientList)
        }

        Closure configureClosure // TODO Pluralize
        def configure(Closure configureClosure) {
            // save for later
            this.configureClosure = configureClosure
        }
    }

    static class HtmlPublisherTarget {
        String reportName
        String reportDir
        String reportFiles
        String keepAll
        String wrapperName // Not sure what this is for
    }

    static class HtmlReportContext implements Context {
        def targets = []
        def report(String reportDir, String reportName = null, String reportFiles = null, Boolean keepAll = null) {

            if(!reportDir) {
                throw new RuntimeException("Report directory for html publisher is required")
            }
            targets << new HtmlPublisherTarget(
                    reportName: reportName?:'',
                    reportDir: reportDir?:'',
                    reportFiles: reportFiles?:'index.html',
                    keepAll: keepAll?'true':'false',
                    wrapperName: 'htmlpublisher-wrapper.html')
        }

        def report(Map args) {
            report(args.reportDir, args.reportName, args.reportFiles, args.keepAll)
        }
    }
}