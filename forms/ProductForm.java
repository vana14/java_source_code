package com.wp.web.forms;

import com.wp.crypto.Hash;
import com.wp.model.PropertyName;
import com.wp.model.composite.PropertySimple;
import com.wp.model.enums.TypesEnum;
import static com.wp.model.enums.TypesEnum.INTERVAL;
import static com.wp.model.enums.TypesEnum.NUMBER;
import com.wp.model.objects.Items;
import com.wp.utils.Cast;
import com.wp.utils.Is;
import com.wp.utils.MB;
import com.wp.web.exceptions.BadRequestException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.*;

/**
 * Данный класс представляет из себя форму с данными, которые необходимы для сохранения товара
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
public class ProductForm extends SaveFiltersForm {

    // название товара
    @NotNull(message = "Не указан заголовок.")
    @Size(min = 1, max = 128, message = "Заголовок должен быть больше 1 и меньше 128 символов.")
    private String title;

    // описание товара
    @NotNull(message = "Не указано описание.")
    @Size(min = 1, max = 16384, message = "Описание должно быть больше 1 и меньше 16384 символов.")
    private String description;

    // система размеров, в которой был сохранен товар
    private String dimension;

    // идентификатор группы, в которой лежит товар
    private Long group_id;

    // хэш от конфигурации товара
    private String hash;

    // опубликован или нет
    private int is_publish = 0;

    // список id изображений для товара
    private List<Long> images;
    
    // название фильтря для цены на английском
    private String price_alias;
    
    // индентификатор первого товара, к которому добавляется конфигурация
    private Long first_object_id;

    // <editor-fold defaultstate="collapsed" desc="Подготавливаем данные для сохранения">
    /**
     * Данный метод формируется свойства для товара перед их сохранением
     * @return
     */
    public List<PropertySimple> toAdditionalFiltersProps() {
        List<PropertySimple> list = new ArrayList<PropertySimple>();

        list.add(new PropertySimple(PropertyName.TITLE, title));
        list.add(new PropertySimple(PropertyName.DESCRIPTION, description));
        list.add(new PropertySimple(PropertyName.IS_PUBLISH, is_publish));

        if (!Is.Empty(filters)) {
            list.add(new PropertySimple(PropertyName.HASH, Hash.getHex(filters)));
        }

        if (dimension != null && !dimension.trim().equals("")) {
            list.add(new PropertySimple(PropertyName.DIMENSION, dimension));
        }

        if (hash != null && !hash.trim().equals("")) {
            list.add(new PropertySimple(PropertyName.HASH, hash));
        }

        if (group_id != null && group_id > 0) {
            list.add(new PropertySimple(PropertyName.GROUP_ID, group_id));
        }

        if (images != null && !images.isEmpty()) {
            List<Items> imagesList = new ArrayList<Items>();
            for (long image_id : images) {
                imagesList.add(new Items(image_id));
            }
            list.add(new PropertySimple(PropertyName.IMAGES, imagesList));
        }
        if (filters_parse != null) {
            for (LinkedHashMap<?, ?> f : filters_parse) {
                long value;
                long valueTo;
                String valueType = null;
                LinkedHashMap valuesMap = null;
                String valueString = null;
                String rightName = FILTER_PREFIX + MB.escape(f.get(ALIAS) + "", MB.escapeModes.html_sql_js);//убираем все лишнее из имени
                switch (TypesEnum.findTE(f.get(TYPE) + "")) {
                    case NUMBER:
                        valuesMap = (LinkedHashMap) f.get(VALUES);
                        valueString = valuesMap.get(VALUE)+"";
                        value = Cast.toLong(valueString);
                        if (value == -1) {
                            list.add(new PropertySimple(PropertyName.PRICE, valueString));
                        } else {
                            list.add(new PropertySimple(PropertyName.PRICE, value));
                            list.add(new PropertySimple(PropertyName.PRICE_TO, value));
                        }
                        break;
                    case INTERVAL:
                        valuesMap = (LinkedHashMap) f.get(VALUES);
                        if (rightName.replace(FILTER_PREFIX, "").equals(price_alias)) {
                            if (f.get(VALUES) instanceof Map) {
                                valuesMap = (LinkedHashMap) f.get(VALUES);
                                valueString = valuesMap.get(VALUE)+"";
                                if (Is.Empty(valueString)) {
                                    valueString = "0";
                                }
                                value = Cast.toLong(valueString);
                                valueTo = Cast.toLong(valuesMap.get(VALUE_TO)+"");
                                if (value == -1) {
                                    list.add(new PropertySimple(PropertyName.PRICE, valueString));
                                    continue;
                                }
                                if (valueTo == -1) {
                                    valueTo = 0l;
                                }
                                if (valueTo > 0l && valueTo < value) {
                                    throw new BadRequestException("Неправильно передан интервал фильтра.");
                                }
                                if (rightName.replace(FILTER_PREFIX, "").equals(price_alias)) {
                                    list.add(new PropertySimple(PropertyName.PRICE, value));
                                    list.add(new PropertySimple(PropertyName.PRICE_TO, valueTo));
                                }
                            }

                        }
                        break;
                }
            }
        }

        return list;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Геттеры и сетторы">
    public Long getFirst_object_id() {
        return first_object_id;
    }

    public void setFirst_object_id(Long first_object_id) {
        this.first_object_id = first_object_id;
    }
    
    public String getPrice_alias() {
        return price_alias;
    }

    public void setPrice_alias(String price_alias) {
        this.price_alias = price_alias;
    }

    public int getIs_publish() {
        return is_publish;
    }

    public void setIs_publish(int is_publish) {
        this.is_publish = is_publish;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getGroup_id() {
        return group_id;
    }

    public void setGroup_id(Long group_id) {
        this.group_id = group_id;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getImages() {
        return images;
    }

    public void setImages(List<Long> images) {
        this.images = images;
    }
    // </editor-fold>
}
