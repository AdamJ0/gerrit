# WANdisco Replicated Fork - GerritMS

## Official Jenkins Build Jobs

Jenkins Build Jobs -
[GerritMS - Builds](https://build-jenkins.wandisco.com/view/Gerrit-MS/)


## Local Development Build Setup

### Asset Repository Information
By default the system is setup to pull any WANDisco specific assets, which are using the WD repository header, to use
the public artifact location on 'WANDisco Nexus (nexus.wandisco.com)'.  

This allows this build assets to be available outside of the WANDisco network for any of our public and 
open sourced assets.

This default location is specified in the bazel local.properties file.

For example:
  ```download.WANDISCO = https://nexus.wandisco.com/repository/wd_releases```


To allow this value to be specified for your own organization or local artifactory server ( when testing new assets ) the
value of the WANDISCO repository can be overridden.
Please note you should not change the default value in the local.properties within the gerrit repository - this is the default
value to be used when open sourcing or building outside WANdisco domain. 
Instead, we allow the specification of values for all the gerrit bazel builds, by using a per-user override.  
This can be achieved by placing a new local.properties file in your users gerrit code review directory.  

If operating internal to the WANdisco network there is a script to perform this setup:
https://gerrit.wandisco.com/plugins/gitiles/gerrit/alm-dev/+/refs/heads/master/env-setup/setupGerritWDRepository.sh

If you wish to set up this value manually:

1) Go to ~/.gerritcodereview directory.
2) Create a new file called local.properties.
3) Create a new property value to override the repository:  
   ```download.WANDISCO = https://myrepo.com/artifactory/myassets```
4) Save the file



## Building Codebase and Installers

To build the replicated source tree, by default we have added some simplified build wrappers.
The wrappers, perform several build steps which allow building / testing and deploying of all the assets required.

_Makefile wrapper around bazel / bazelisk targets._


To run a full build including all compilation / tests and installers  
```make all```

to run a fast build with only minimal code for release.war  
```make fast-assembly```

to run a fast build but include the installers  
```make fast-assembly installer```

to run all tests  
```make tests```

to run a single test  
```make run-single-test testFilter=CustomGsonDeserializerTest testLocation=//javatests/com/google/gerrit/server:server_tests```

to run a single test but suspend and wait for remote debugging:  
```make run-single-test testFilter=CustomGsonDeserializerTest testLocation=//javatests/com/google/gerrit/server:server_tests testOptionalArgs=--java_debug```




### Building Using Maven Wrapper

The project can be built using maven. Any Makefile target can be called via passing the target as a property.
The makefile is then run via maven using the exec-maven-plugin. We use a default makeTarget if none are specified as
a property. The default make target we use is '**all**'
```
<build>
     <plugins>
       <plugin>
         <groupId>org.codehaus.mojo</groupId>
         <artifactId>exec-maven-plugin</artifactId>
         <version>1.3.2</version>
         <executions>
           <execution>
             <id>build-all-conditionally</id>
             <phase>package</phase>
             <goals>
               <goal>exec</goal>
             </goals>
             <configuration>
               <executable>make</executable>
               <workingDirectory>${project.basedir}/..</workingDirectory>
               <arguments>
                 <argument>${makeTarget}</argument>
               </arguments>
             </configuration>
           </execution>
```

The following properties have the following default values. 
```
    <release>false</release>
    <officialBuild>false</officialBuild>
    <RUN_INTEGRATION_TESTS>true</RUN_INTEGRATION_TESTS>
```

To run using the default Makefile target you can simply run (Note, this will also run the acceptance tests)
```
mvn install 
```

To run a specific target using the Makefile you can do the following (example target is list-assets):
```
mvn install -DRUN_INTEGRATION_TESTS=false -DmakeTarget=list-assets
```

Or if running in Jenkins you would pass the following. Tests are run by default.
```
mvn install -Drelease=false -DofficialBuild=true -DmakeTarget=list-assets
```

There is also a dedicated execution profile for deployment. This will by default deploy all gerrit assets.
The execution profile also sets environment variables which are in turn used by 
the Makefile in order to make decisions on whether we need to 
* setup a WANdisco environment, 
* run acceptance tests
* check for SNAPSHOT if we are performing an official release.

```
       <execution>
             <id>deploy-assets</id>
             <phase>deploy</phase>
             <goals>
               <goal>exec</goal>
             </goals>
             <configuration>
               <executable>make</executable>
               <workingDirectory>${project.basedir}/..</workingDirectory>
               <arguments>
                 <argument>deploy-all-gerrit</argument>
               </arguments>
             </configuration>
           </execution>
         </executions>
         <configuration>
           <environmentVariables>
             <RUN_INTEGRATION_TESTS>${RUN_INTEGRATION_TESTS}</RUN_INTEGRATION_TESTS>
             <officialBuild>${officialBuild}</officialBuild>
             <release>${release}</release>
           </environmentVariables>
          </configuration>
       </plugin>
     </plugins>
   </build>
```

### Update Version Information
There is now a dedicated Makefile target that will primarily be used in conjunction with the release tooling in order to
roll-on / update the Gerrit project version. This can be done manually but is not recommended as the release-tooling
should be the only thing updating the project version.

The update-version-information target calls the tools/version.py script which is responsible for finding all poms
recursively in the Gerrit project and updating their version to the specified version. The version.bzl file is also
updated. The specified version is given by passing an environment variable called NEW_PROJECT_VERSION.

For the exec-maven-plugin we have a validate-project execution which is tied directly to the maven validate phase.
```
          <execution>
            <id>validate-project</id>
            <phase>validate</phase>
            <goals>
              <goal>exec</goal>
            </goals>
            <configuration>
              <executable>make</executable>
              <workingDirectory>${project.basedir}/..</workingDirectory>
              <arguments>
                <argument>validate</argument>
              </arguments>
              <environmentVariables>
                <NEW_PROJECT_VERSION>${NEW_PROJECT_VERSION}</NEW_PROJECT_VERSION>
              </environmentVariables>
            </configuration>
          </execution>
```

#### Ways to run:
Using maven, we can set the new project version as follows:
```
mvn validate -DNEW_PROJECT_VERSION=x.x.x.x 
```
This result in the Makefile target called validate being called. The 'validate' target will first check that 
NEW_PROJECT_VERSION is not set to false (false is the default). If the value is not  false it will then call 
the 'update-version-information' target.

**Warning:** Not recommended way of using this as the release tooling will primarily be responsible for 
setting the new version.
```
export NEW_PROJECT_VERSION=x.x.x.x && make update-version-information
```

## Additional Tools

- [tools/check_sha.py](https://workspace.wandisco.com/display/GIT/GerritMS+Build+Helper+Scripts)
  Script to automatically update bazel dependencies in the build files.

  Typical usage to update all bazel dependencies for org.eclipse.jgit.\* and com.wandisco.\*
  using their default configured repositories would be:

      $ tools/check_sha.py -vpe

  For full usage options, run:

      $ tools/check_sha.py -h
