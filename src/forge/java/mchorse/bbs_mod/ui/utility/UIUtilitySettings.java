package mchorse.bbs_mod.ui.utility;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10nUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStatusLogOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.CDNAssetSyncService;
import net.minecraft.client.Minecraft;

public class UIUtilitySettings extends UIElement
{
    public UIUtilitySettings(boolean language, boolean cdn)
    {
        this.column(5).vertical().stretch();
        if (language)
        {
            UIButton analyze = new UIButton(UIKeys.UTILITY_ANALYZE_LANG, b -> this.analyzeLanguageStrings());
            UIButton compile = new UIButton(UIKeys.UTILITY_COMPILE_LANG, b -> this.compileLanguageStrings());
            UIButton editor = new UIButton(UIKeys.UTILITY_LANG_EDITOR, b -> this.openLangEditor());
            this.add(UI.label(UIKeys.UTILITY_LANG_LABEL), UI.row(analyze, compile), editor.marginBottom(UIConstants.SECTION_GAP));
        }
        if (!cdn) return;
        UIButton cdnDownload = new UIButton(UIKeys.GENERAL_DOWNLOAD, (b) ->
        {
            UIStatusLogOverlayPanel panel = new UIStatusLogOverlayPanel(UIKeys.CDN_DOWNLOADING_TITLE);

            UIOverlay.addOverlay(this.getContext(), panel);

            Thread thread = new Thread(() ->
            {
                BBSResources.stopWatchdog();

                try
                {
                    CDNAssetSyncService syncService = new CDNAssetSyncService(BBSSettings.cdnUrl.get(), BBSMod.getAssetsFolder().toPath(), (p) ->
                    {
                        Minecraft.getMinecraft().addScheduledTask(() -> panel.list.add(new Pair<>(p.a.color, p.b)));
                    });

                    syncService.syncOnce();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                BBSResources.setupWatchdog();

                Minecraft.getMinecraft().addScheduledTask(() ->
                {
                    BBSModClient.getTextures().delete();
                    BBSModClient.getSounds().deleteSounds();
                    BBSModClient.getModels().reload();
                });
            }, "CDNDownloadThread");

            thread.start();
        });

        UIButton cdnUpload = new UIButton(UIKeys.GENERAL_UPLOAD, (b) ->
        {
            UIStatusLogOverlayPanel panel = new UIStatusLogOverlayPanel(UIKeys.CDN_UPLOADING_TITLE);

            UIOverlay.addOverlay(this.getContext(), panel);

            Thread thread = new Thread(() ->
            {
                try
                {
                    CDNAssetSyncService syncService = new CDNAssetSyncService(BBSSettings.cdnUrl.get(), BBSMod.getAssetsFolder().toPath(), (p) ->
                    {
                        Minecraft.getMinecraft().addScheduledTask(() -> panel.list.add(new Pair<>(p.a.color, p.b)));
                    });

                    syncService.pushChangedFiles(BBSSettings.cdnToken.get());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }, "CDNUploadThread");

            thread.start();
        });


        this.add(UI.label(IKey.raw("CDN")));
        for (UIElement element : UIValueMap.create(BBSSettings.cdnUrl, this)) this.add(element);
        for (UIElement element : UIValueMap.create(BBSSettings.cdnToken, this)) this.add(element);
        this.add(UI.row(cdnDownload, cdnUpload));
    }

    private void openLangEditor()
    {
        UIOverlay.addOverlay(this.getContext(), new UILanguageEditorOverlayPanel(), 0.6F, 0.9F);
    }

    private void analyzeLanguageStrings()
    {
        this.print(L10nUtils.analyzeStrings(BBSModClient.getL10n()));
    }

    private void compileLanguageStrings()
    {
        L10nUtils.compile(BBSMod.getExportFolder(), BBSModClient.getL10n().getStrings());

        UIMessageFolderOverlayPanel panel = new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.UTILITY_COMPILE_LANG_DESCRIPTION, BBSMod.getExportFolder());
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void print(String string)
    {
        int longest = 0;
        String[] splits = string.split("\n");

        for (String s : splits)
        {
            longest = Math.max(s.length(), longest);
        }

        String separator = StringUtils.repeat("-", longest);

        System.out.println(separator);

        for (String s : splits)
        {
            System.out.println(s);
        }

        System.out.println(separator);
    }

}
