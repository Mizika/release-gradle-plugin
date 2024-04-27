package common.gradle.plugin.release

import com.github.zafarkhaja.semver.Version
import groovy.transform.CompileDynamic
import nebula.plugin.release.ReleaseExtension
import nebula.plugin.release.git.opinion.Strategies
import nebula.plugin.release.git.semver.*
import org.gradle.api.GradleException
import org.gradle.api.Project

import java.util.regex.Pattern

import static nebula.plugin.release.git.semver.StrategyUtil.all
import static nebula.plugin.release.git.semver.StrategyUtil.closure

@CompileDynamic
class GitlabCIStrategies {


    static SemVerStrategy SNAPSHOT(Project project) {
        Strategies.SNAPSHOT.copyWith(normalStrategy: getScopes(project))
    }

    static SemVerStrategy DEVELOPMENT(Project project) {
        Strategies.SNAPSHOT.copyWith(
                name: 'development',
                stages: ['dev'] as SortedSet,
                allowDirtyRepo: true,
                preReleaseStrategy: BuildMetadata.SNAPSHOT,
                createTag: false,
                enforcePrecedence: false,
                normalStrategy: getScopes(project),
                buildMetadataStrategy: Strategies.BuildMetadata.NONE)
    }

    static SemVerStrategy IMMUTABLE_SNAPSHOT(Project project) {
        Strategies.IMMUTABLE_SNAPSHOT.copyWith(
                normalStrategy: getScopes(project),
                buildMetadataStrategy: BuildMetadata.DEVELOPMENT_METADATA_STRATEGY)
    }

    static SemVerStrategy PRE_RELEASE(Project project) {
        Strategies.PRE_RELEASE.copyWith(normalStrategy: getScopes(project))
    }

    static SemVerStrategy FINAL(Project project) {
        Strategies.FINAL.copyWith(normalStrategy: getScopes(project))
    }

    private static getScopes(Project project) {
        println("Call getScopes")
        final PartialSemVerStrategy GITLAB_FLOW_BRANCH_MAJOR_X = fromGitlabEnvPropertyPattern(~/^(release|feature|candidate)(?:\/|-)(\d+)\.x$/)
        final PartialSemVerStrategy GITLAB_FLOW_BRANCH_MAJOR_MINOR_X = fromGitlabEnvPropertyPattern(~/^(release|feature|candidate)(?:\/|-)(\d+)\.(\d+)\.x$/)
        final PartialSemVerStrategy NEAREST_HIGHER_ANY = nearestHigherAny()
        StrategyUtil.one(Strategies.Normal.USE_SCOPE_PROP,
                GITLAB_FLOW_BRANCH_MAJOR_X, GITLAB_FLOW_BRANCH_MAJOR_MINOR_X,
                Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
                Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_MINOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_MINOR_X,
                NEAREST_HIGHER_ANY, Strategies.Normal.useScope(ChangeScope.MINOR))
    }

    static PartialSemVerStrategy fromGitlabEnvPropertyPattern(Pattern pattern) {
        return StrategyUtil.closure { state ->
            if (System.getenv('CI') != null) {
                def branch = System.getenv('CI_COMMIT_REF_NAME')
                def m = branch =~ pattern
                if (m) {
                    def major = m.groupCount() >= 1 ? StrategyUtil.parseIntOrZero(m[0][2]) : -1
                    def minor = m.groupCount() >= 2 ? StrategyUtil.parseIntOrZero(m[0][3]) : -1

                    def normal = state.nearestVersion.normal
                    def majorDiff = major - normal.majorVersion
                    def minorDiff = minor - normal.minorVersion
                    if (majorDiff > 1 || minorDiff > 1) {
                        return state.copyWith(inferredNormal: Version.forIntegers(major, minor).normalVersion)
                    } else if (majorDiff == 1 && minor <= 0) {
                        // major is off by one and minor is either 0 or not in the branch name
                        return StrategyUtil.incrementNormalFromScope(state, ChangeScope.MAJOR)
                    } else if (minorDiff == 1 && minor > 0) {
                        // minor is off by one and specified in the branch name
                        return StrategyUtil.incrementNormalFromScope(state, ChangeScope.MINOR)
                    } else if (majorDiff == 0 && minorDiff == 0 && minor >= 0) {
                        // major and minor match, both are specified in branch name
                        return StrategyUtil.incrementNormalFromScope(state, ChangeScope.PATCH)
                    } else if (majorDiff == 0 && minor < 0) {
                        // only major specified in branch name and already matches
                        return state
                    } else {
                        throw new GradleException("Invalid branch (${state.currentBranch.name}) for nearest normal (${normal}).")
                    }
                }
            }

            return state
        }
    }

    /**
     * If the nearest any is higher from the nearest normal, sets the
     * normal component to the nearest any's normal component. Otherwise
     * do nothing.
     *
     * <p>
     * For example, if the nearest any is {@code 1.2.3-alpha.1} and the
     * nearest normal is {@code 1.2.2}, this will infer the normal
     * component as {@code 1.2.3}.
     * </p>
     */
    static private PartialSemVerStrategy nearestHigherAny() {
        return StrategyUtil.closure { SemVerStrategyState state ->
            def nearest = state.nearestVersion
            if (nearest.any.lessThanOrEqualTo(nearest.normal)) {
                return state
            } else {
                return state.copyWith(inferredNormal: nearest.any.normalVersion)
            }
        }
    }

    @CompileDynamic
    static final class BuildMetadata {
        static ReleaseExtension nebulaReleaseExtension

        static final PartialSemVerStrategy SNAPSHOT = closure { state ->
            boolean needsBranchMetadata = true
            nebulaReleaseExtension.releaseBranchPatterns.each {
                if (state.currentBranch.name =~ it) {
                    needsBranchMetadata = false
                }
            }
            String shortenedBranch = (state.currentBranch.name =~ nebulaReleaseExtension.shortenedBranchPattern)[0][1]
            shortenedBranch = shortenedBranch.replaceAll(/[\/\\.]/, '-')
            def metadata = needsBranchMetadata ? "SNAPSHOT-${shortenedBranch}" : "SNAPSHOT"
            return state.copyWith(inferredPreRelease: metadata)
        }

        static final PartialSemVerStrategy DEVELOPMENT_METADATA_STRATEGY = { state ->
            boolean needsBranchMetadata = true
            nebulaReleaseExtension.releaseBranchPatterns.each {
                if (state.currentBranch.name =~ it) {
                    needsBranchMetadata = false
                }
            }
            def metadata = state.currentHead.abbreviatedId
            state.copyWith(inferredBuildMetadata: metadata)
        }
    }
}
