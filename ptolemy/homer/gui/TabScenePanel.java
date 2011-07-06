/*
 TODO
 
 Copyright (c) 2011 The Regents of the University of California.
 All rights reserved.
 Permission is hereby granted, without written agreement and without
 license or royalty fees, to use, copy, modify, and distribute this
 software and its documentation for any purpose, provided that the above
 copyright notice and the following two paragraphs appear in all copies
 of this software.

 IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 ENHANCEMENTS, OR MODIFICATIONS.

 PT_COPYRIGHT_VERSION_2
 COPYRIGHTENDKEY
 */
package ptolemy.homer.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.IOException;
import java.util.List;

import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.TwoStateHoverProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.border.Border;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.model.ObjectScene;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;

import ptolemy.homer.kernel.WidgetLoader;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.util.MessageHandler;
import ptolemy.vergil.toolbox.PtolemyTransferable;

//////////////////////////////////////////////////////////////////////////
//// TabScenePanel

/**
 *
 * @author Anar Huseynov
 * @version $Id$ 
 * @since Ptolemy II 8.1
 * @Pt.ProposedRating Red (ahuseyno)
 * @Pt.AcceptedRating Red (ahuseyno)
 */
public class TabScenePanel {

    /**
     * TODO
     */
    public TabScenePanel() {
        _scene = new ObjectScene();
        _mainLayer = new LayerWidget(getScene());
        _interactionLayer = new LayerWidget(getScene());
        _scene.createView();
        getScene().addChild(_interactionLayer);
        getScene().addChild(_mainLayer);
        _resizeAction = ActionFactory.createAlignWithResizeAction(_mainLayer,
                _interactionLayer, null, false);
        _moveAction = ActionFactory.createAlignWithMoveAction(_mainLayer,
                _interactionLayer, null, false);
        _hoverProvider = new TwoStateHoverProvider() {

            public void unsetHovering(Widget widget) {
                widget.setBorder(DEFAULT_BORDER);
            }

            public void setHovering(Widget widget) {
                widget.setBorder(RESIZE_BORDER);
            }
        };
        _hoverAction = ActionFactory.createHoverAction(_hoverProvider);
        _scene.getActions().addAction(_hoverAction);
        new DropTarget(_scene.getView(), new DropTargetAdapter() {

            public void drop(DropTargetDropEvent dropEvent) {

                if (dropEvent
                        .isDataFlavorSupported(PtolemyTransferable.namedObjFlavor)) {
                    try {
                        dropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                        List<?> dropObjects = (java.util.List) dropEvent
                                .getTransferable().getTransferData(
                                        PtolemyTransferable.namedObjFlavor);

                        NamedObj dropObj = (NamedObj) dropObjects.get(0);
                        Widget widget = WidgetLoader.loadWidget(_scene,
                                dropObj, dropObj.getClass());
                        addWidget(widget, dropEvent.getLocation());
                    } catch (UnsupportedFlavorException e) {
                        MessageHandler.error(
                                "Can't find a supported data flavor for drop in "
                                        + dropEvent, e);
                        return;
                    } catch (IOException e) {
                        MessageHandler.error(
                                "Can't find a supported data flavor for drop in "
                                        + dropEvent, e);
                        return;
                    } catch (IllegalActionException e) {
                        MessageHandler.error(
                                "Can't initialize widget for the selected object "
                                        + dropEvent, e);
                        return;
                    } catch (NameDuplicationException e) {
                        MessageHandler.error(
                                "Can't initialize widget for the selected object "
                                        + dropEvent, e);
                        return;
                    }
                } else {
                    dropEvent.rejectDrop();
                }
            }
        });
    }

    /**
     * TODO
     * @param widget
     * @param location
     */
    public void addWidget(Widget widget, Point location) {
        widget.setPreferredLocation(location);
        widget.getActions().addAction(_resizeAction);
        widget.getActions().addAction(_moveAction);
        widget.getActions().addAction(_hoverAction);
        widget.setBorder(DEFAULT_BORDER);
        _mainLayer.addChild(widget);
        _scene.validate();
    }

    /**
     * @return
     */
    public Component getView() {
        return getScene().getView();
    }

    /**
     * @return the _scene
     */
    public ObjectScene getScene() {
        return _scene;
    }

    private static final Border RESIZE_BORDER = BorderFactory
            .createResizeBorder(6, Color.BLACK, true);
    private static final Border DEFAULT_BORDER = BorderFactory
            .createEmptyBorder(6);
    private final LayerWidget _mainLayer;
    private final LayerWidget _interactionLayer;
    private final ObjectScene _scene;
    private final WidgetAction _moveAction;
    private final WidgetAction _resizeAction;
    private final TwoStateHoverProvider _hoverProvider;
    private WidgetAction _hoverAction;
}
